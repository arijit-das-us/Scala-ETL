package io.kymeta.data.synthetic

import org.apache.spark.sql.types.{ DoubleType, IntegerType, TimestampType }
import org.apache.spark.sql.magellan.dsl.expressions._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.sql.functions._
import com.twosigma.flint.timeseries._

import scala.concurrent.duration._

object SNR {
  def loadASMCentric(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val asmmetrics = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/asm.metrics")
      .select(
        $"timestamp-S" as "time",
        $"ASMSerial",
        point($"longitude", $"latitude") as "Location",
        $"longitude",
        $"latitude",
        $"pl-esno",
        $"rx-theta",
        $"antenna-frequency-rx"
      )

    val devices = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.devices")
      .select($"AsmSerial" as "ASMSerial", $"ModemSerial" cast IntegerType)
      .na.drop

    val remotemetrics = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/remotemetrics")

    val pulsebeam = remotemetrics // Beam 1698
      .filter($"MetricInt" === 1698)
      .select($"timestamp" as "time", $"ModemSerial" cast IntegerType, $"Data" as "BeamID" cast IntegerType)

    val pulsesnr = remotemetrics // Current SNR 1098
      .filter($"MetricInt" === 1098)
      .select($"timestamp" as "time", $"ModemSerial" cast IntegerType, $"Data" as "SNR")

    val kalo_8w_beam = spark.read.option("header", true).csv("wasbs://coverage@kymetabigdata.blob.core.windows.net/kalo_8w_beammap.csv")

    val kalo_8w_rtn = spark
      .read
      .format("magellan")
      .option("type", "geojson")
      .load("wasbs://coverage@kymetabigdata.blob.core.windows.net/kalo_8w_rtn_measured_horizontal.fwd_cn.geojson")
      .withColumn("efficiency", $"metadata.efficiency")
      .withColumn("fwd_cn", $"metadata.fwd_cn (dB)")
      .withColumn("modCod", $"metadata.modCod")
      .withColumn("modem", $"metadata.modem")
      .withColumn("KALOCovBeamName", $"metadata.beamInfo")
      .drop("metadata", "point", "polyline")

    val kalo_8w_fwd = spark
      .read
      .format("magellan")
      .option("type", "geojson")
      .load("wasbs://coverage@kymetabigdata.blob.core.windows.net/kalo_8w_fwd_measured_horizontal.final.geojson")
      .withColumn("efficiency", $"metadata.Efficiency")
      .withColumn("fwd_cn", $"metadata.FWD_CN (dB)")
      .withColumn("modCod", $"metadata.ModCod")
      .withColumn("modem", $"metadata.Modem")
      .withColumn("KALOCovBeamName", $"metadata.Beam Info")
      .drop("metadata", "point", "polyline")

    val asmmodem = asmmetrics.join(devices, Seq("ASMSerial")).na.drop

    val modembeam = pulsebeam.join(kalo_8w_beam, Seq("BeamID")).drop("BeamID").na.drop

    val asmmodemTS = TimeSeriesRDD.fromDF(asmmodem)(isSorted = false, timeUnit = SECONDS)
    val modembeamTS = TimeSeriesRDD.fromDF(modembeam)(isSorted = false, timeUnit = SECONDS)
    val pulsesnrTS = TimeSeriesRDD.fromDF(pulsesnr)(isSorted = false, timeUnit = SECONDS)

    val joined = asmmodemTS
      .leftJoin(right = modembeamTS, key = Seq("ModemSerial"), tolerance = "100d")
      .leftJoin(right = pulsesnrTS, key = Seq("ModemSerial"), tolerance = "1day").toDF.na.drop

    joined
      .join(kalo_8w_fwd, $"FWD" === $"KaloCovBeamName").where($"Location" within $"polygon")
      .select(
        to_utc_timestamp(
          $"time" / lit(1000000000) cast TimestampType, "PDT"
        ) as "time",
        $"ASMSerial",
        $"Latitude",
        $"Longitude",
        $"ModemSerial",
        $"PulseBeamName",
        $"KALOCovBeamName",
        $"pl-esno" as "pl-esno (ASMMetrics)",
        $"SNR" as "SNR (Pulse)",
        $"fwd_cn" cast DoubleType as "C/N (kalo_8w_fwd)",
        $"rx-theta" as "rx-theta (ASMMetrics)",
        $"antenna-frequency-rx" as "antenna-frequency-rx (ASMMetrics)"
      )
  }
}
