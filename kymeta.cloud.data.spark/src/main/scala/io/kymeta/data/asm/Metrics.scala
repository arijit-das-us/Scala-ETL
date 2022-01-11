package io.kymeta.data.asm

import com.microsoft.azure.storage.blob.CloudBlobContainer
import io.kymeta.data.{ blobClient, wasbsPath }
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ DataFrame, SparkSession }

import scala.collection.JavaConverters._

/*
Example ASMMetrics.2.json file

{
    "+1.1V_D (V)": "1.099", // These can only be read in as StringType
    "+1.5V_D (V)": "1.492", // Then needs to be cast to DoubleType
    "+2.5V_D (V)": "2.476",
    "+3.3V_D (V)": "3.272",
    "+5V (V)": "4.930",
    "-20V (V)": "-20.589",
    "Analog voltage (V)": "3.306",
    "Board input current (A)": "0.413",
    "Board input voltage (V)": "29.950",
    "Clock voltage (V)": "3.311",
    "Gate Current (A)": "0.011",
    "Gate Voltage (V)": "20.250",
    "Heater Control (V)": "1.980",
    "Quadrant 1 Thermistor (C)": "24.072",
    "Quadrant 2 Thermistor (C)": "24.455",
    "Quadrant 3 Thermistor (C)": "24.510",
    "Quadrant 4 Thermistor (C)": "24.127",
    "Relative Humidity (%)": "15.365",
    "Source current (A)": "0.046",
    "Source voltage (V)": "15.250",
    "Temperature (C)": "31.306",
    "altitude": 0.0,
    "direction": 0.0,
    "gps_fix": 0,
    "gps_fix_quality": 0,
    "latitude": 89.9,
    "longitude": 0.0,
    "speed": 0.0,
    "timestamp-S": 1547786073
}

 */

object Metrics {
  // Original, sql.types, nullable, InternalName
  private val colTypesUnchanged: Array[Tuple3[String, DataType, Boolean]] =
    Array(
      ("agc", LongType, true),
      ("altitude", DoubleType, true),
      ("antenna-frequency-rx", LongType, true),
      ("antenna-frequency-tx", LongType, true),
      ("antenna-polarization-type-rx", StringType, true),
      ("antenna-polarization-type-tx", StringType, true),
      ("antenna-type", StringType, true),
      ("direction", DoubleType, true),
      ("gps_fix", LongType, true),
      ("gps_fix_quality", LongType, true),
      ("latitude", DoubleType, true),
      ("longitude", DoubleType, true),
      ("mode", StringType, true),
      ("pl-esno", DoubleType, true),
      ("pl-sync", LongType, true),
      ("r-esno", DoubleType, true),
      ("rx-lock", BooleanType, true),
      ("rx-lpa", DoubleType, true),
      ("rx-phi", DoubleType, true),
      ("rx-theta", DoubleType, true),
      ("speed", DoubleType, true),
      ("target-altitude", DoubleType, true),
      ("target-latitude", DoubleType, true),
      ("target-longitude", DoubleType, true),
      ("target-polarization-rx", DoubleType, true),
      ("target-polarization-tx", DoubleType, true),
      ("target-symbol-rate-msyms", DoubleType, true),
      ("target-type", StringType, true),
      ("timestamp-S", LongType, true),
      ("tx-lpa", DoubleType, true),
      ("tx-phi", DoubleType, true),
      ("tx-theta", DoubleType, true)
    )

  // These have to be read in as StringType as data values are double-quoted
  // They are converted to DoubleType before being persisted in .parquet
  val colTypesToRename: Array[Tuple4[String, DataType, Boolean, String]] =
    Array(
      ("+1.1V_D (V)", StringType, true, "+1_1V_D"),
      ("+1.5V_D (V)", StringType, true, "+1_5V_D"),
      ("+2.5V_D (V)", StringType, true, "+2_5V_D"),
      ("+3.3V_D (V)", StringType, true, "+3_3V_D"),
      ("+5V (V)", StringType, true, "+5V_D"),
      ("-20V (V)", StringType, true, "-20V_D"),
      ("Analog voltage (V)", StringType, true, "Analog_voltage__V"),
      ("Board input current (A)", StringType, true, "Board_input_current__A"),
      ("Board input voltage (V)", StringType, true, "Board_input_voltage__V"),
      ("Clock voltage (V)", StringType, true, "Clock_voltage__V"),
      ("Gate Current (A)", StringType, true, "Gate_Current__A"),
      ("Gate Voltage (V)", StringType, true, "Gate_Voltage__V"),
      ("Heater Control (V)", StringType, true, "Heater_Control__V"),
      ("Quadrant 1 Thermistor (C)", StringType, true, "Quadrant_1_Thermistor__C"),
      ("Quadrant 2 Thermistor (C)", StringType, true, "Quadrant_2_Thermistor__C"),
      ("Quadrant 3 Thermistor (C)", StringType, true, "Quadrant_3_Thermistor__C"),
      ("Quadrant 4 Thermistor (C)", StringType, true, "Quadrant_4_Thermistor__C"),
      ("Relative Humidity (%)", StringType, true, "Relative_Humidity__Percent"),
      ("Source current (A)", StringType, true, "Source_current__A"),
      ("Source voltage (V)", StringType, true, "Source_voltage__V"),
      ("Temperature (C)", StringType, true, "Temperature__C")
    )

  private val colTypes = colTypesToRename ++ colTypesUnchanged
    .map(col => Tuple4(col._1, col._2, col._3, col._1))

  val schema = StructType(colTypes.map(col => StructField(
    col._1,
    col._2,
    col._3,
    Metadata.fromJson(s"""{ "rename": "${col._4}" }""")
  )))

  val colMapForward = Map(schema.map(struct => struct.name -> struct.metadata.getString("rename")): _*)
  val colMapBackward = Map(schema.map(struct => struct.metadata.getString("rename") -> struct.name): _*)

  private val container = "rawasmmetrics"

  private val blobContainer: CloudBlobContainer = blobClient.getContainerReference(container)

  val wasbsRaw = wasbsPath(container)
  val wasbsParquet = wasbsPath("access", "asm.metrics")

  def listASMSerials(): Iterable[String] = blobContainer
    /*
    e.g.
    /rawcontext/AAE000J170314037/
    /rawcontext/AAE000J170526086/
    /rawcontext/AAE000J170629107/
    */
    .listBlobs
    .asScala
    .map(_.getUri.getPath.split("/")(2))

  def renameColumns(df: DataFrame, columnMap: Map[String, String]): DataFrame = {
    var renamed = df
    for (colNameMap <- columnMap) {
      renamed = renamed.withColumnRenamed(colNameMap._1, colNameMap._2)
    }
    renamed
  }

  def load(spark: SparkSession): DataFrame = {
    val df = spark.read.parquet(wasbsParquet)
    renameColumns(df, colMapBackward)
  }
}
