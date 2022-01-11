package io.kymeta.data.reports.asm.jobs

import java.sql.{ Date, Timestamp }

import scala.language.postfixOps

import sys.process._

import org.apache.log4j.{ Level, LogManager }

import org.apache.spark.sql.{ Column, DataFrame, SparkSession }
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.TimestampType

object Aggregates {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    val log = LogManager.getRootLogger

    val has_all_confs =
      Set(
        "spark.kymeta.reports.asm.input"
      ).map(spark.conf.getAll.keySet.contains(_)) == Set(true)

    if (!has_all_confs) {
      log.error("Not all confs set")
      return
    }

    createIntervals(spark)
    createBreakdowns(spark)
    jsonify(spark)
  }

  def melt(
    df: DataFrame,
    id_vars: Seq[String],
    value_vars: Seq[String],
    var_name: String = "variable",
    value_name: String = "value"
  ): DataFrame = {

    // Create array<struct<variable: str, value: ...>>
    val _vars_and_vals = array((for (c <- value_vars) yield { struct(lit(c).alias(var_name), col(c).alias(value_name)) }): _*)

    // Add to the DataFrame and explode
    val _tmp = df.withColumn("_vars_and_vals", explode(_vars_and_vals))

    val cols = id_vars.map(col _) ++ { for (x <- List(var_name, value_name)) yield { col("_vars_and_vals")(x).alias(x) } }

    return _tmp.select(cols: _*)
  }

  case class Record(
    ASMSerial: String,
    timestamp: java.sql.Timestamp,
    ASMMetric: String,
    data: Double
  )

  case class aggItem(
      start: Timestamp,
      end: Timestamp,
      records: Long,
      avg: Double,
      max: Double,
      min: Double,
      stddev: Double
  ) extends Ordered[aggItem] {
    import scala.math.Ordered.orderingToOrdered

    def compare(that: aggItem): Int = this.start compare that.start
  }

  // REFACTOR
  // implicit ordering for java.sql.Timestamp
  implicit def ordered: Ordering[java.sql.Timestamp] =
    new Ordering[java.sql.Timestamp] {
      def compare(x: Timestamp, y: Timestamp): Int = x compareTo y
    }

  def createIntervals(spark: SparkSession): Unit = {
    import spark.implicits._

    val rawdata = spark.read.parquet(spark.conf.get("spark.kymeta.reports.asm.input"))

    // metrics name mapping - REFACTOR with io.kymeta.data.asm
    val metrics = Seq(
      ("+1_1V_D (V)", "+1_1V_D", "ASM-SENSOR-1.1V"),
      ("+1.5V_D (V)", "+1_5V_D", "ASM-SENSOR-1.5V"),
      ("+2.5V_D (V)", "+2_5V_D", "ASM-SENSOR-2.5V"),
      ("+3.3V_D (V)", "+3_3V_D", "ASM-SENSOR-3.3V"),
      ("+5V_D (V)", "+5V_D", "ASM-SENSOR-5V"),
      ("-20V_D (V)", "-20V_D", "ASM-SENSOR-20V"),
      ("Quadrant 1 Thermistor (C)", "Quadrant_1_Thermistor__C", "ASM-THERMISTOR-Q1"),
      ("Quadrant 2 Thermistor (C)", "Quadrant_2_Thermistor__C", "ASM-THERMISTOR-Q2"),
      ("Quadrant 3 Thermistor (C)", "Quadrant_3_Thermistor__C", "ASM-THERMISTOR-Q3"),
      ("Quadrant 4 Thermistor (C)", "Quadrant_4_Thermistor__C", "ASM-THERMISTOR-Q4"),
      ("Analog voltage (V)", "Analog_voltage__V", "ASM-SENSOR-ANALOG-VOLTAGE"),
      ("Board input current (A)", "Board_input_current__A", "ASM-BOARD-INPUT-CURRENT"),
      ("Board input voltage (V)", "Board_input_voltage__V", "ASM-BOARD-INPUT-VOLTAGE"),
      ("Clock voltage (V)", "Clock_voltage__V", "ASM-CLOCK-VOLTAGE"),
      ("Gate Current (A)", "Gate_Current__A", "ASM-GATE-CURRENT"),
      ("Gate Voltage (V)", "Gate_Voltage__V", "ASM-GATE-VOLTAGE"),
      ("Heater Control (V)", "Heater_Control__V", "ASM-HEATER-CONTROL-VOLTAGE"),
      ("Relative Humidity (%)", "Relative_Humidity__Percent", "ASM-HUMIDITY-RELATIVE"),
      ("Source current (A)", "Source_current__A", "ASM-SOURCE-CURRENT"),
      ("Source voltage (V)", "Source_voltage__V", "ASM-SOURCE-VOLTAGE"),
      ("Temperature (C)", "Temperature__C", "ASM-TEMPERATURE"),
      // ("agc", "agc", "ASM-AGC"),
      ("altitude", "altitude", "ASM-ALTITUDE"),
      // ("antenna-frequency-rx", "antenna-frequency-rx", "ASM-ANTENNA-FREQUENCY-RX"),
      // ("antenna-frequency-tx", "antenna-frequency-tx", "ASM-ANTENNA-FREQUENCY-TX"),
      ("direction", "direction", "ASM-DIRECTION"),
      ("latitude", "latitude", "ASM-LATITUDE"),
      ("longitude", "longitude", "ASM-LONGITUDE"),
      ("rx-lpa", "rx-lpa", "ASM-RX-LPA"),
      ("rx-phi", "rx-phi", "ASM-RX-PHI"),
      ("rx-theta", "rx-theta", "ASM-RX-THETA"),
      ("tx-lpa", "tx-lpa", "ASM-TX-LPA"),
      ("tx-phi", "tx-phi", "ASM-TX-PHI"),
      ("tx-theta", "tx-theta", "ASM-TX-THETA"),
      ("speed", "speed", "ASM-SPEED"),
      ("target-longitude", "target-longitude", "ASM-TARGET-LONGITUDE"),
      ("target-symbol-rate-msyms", "target-symbol-rate-msyms", "ASM-TARGET-SYMBOL-RATE-MSYMS"),
      ("pl-esno", "pl-esno", "ASM-PL-ESNO")
    )
      .toDF("wiki", "ASMMetric", "kymeta")

    val chosen_metrics = metrics
      .select($"kymeta")
      .map(_.getString(0))
      .collect
      .toList

    val melted = melt(
      rawdata,
      Seq("ASMSerial", "timestamp-S"),
      metrics
        .select($"ASMMetric")
        .map(_.getString(0))
        .collect
        .toList,
      "ASMMetric",
      "melted_data"
    )
      .join(metrics, Seq("ASMMetric"))
      .select(
        $"ASMSerial",
        $"kymeta" as "ASMMetric",
        $"timestamp-S" cast TimestampType as "timestamp",
        $"melted_data" as "data"
      )
      .na.drop
      .dropDuplicates

    val chosen_serials = melted
      .filter($"ASMSerial".startsWith("AAE"))
      .select($"ASMSerial")
      .dropDuplicates
      .sort($"ASMSerial")

    val records = melted
      .join(chosen_serials, Seq("ASMSerial"))
      .select($"ASMSerial", $"timestamp", $"ASMMetric", $"data")
      .na.drop
      .as[Record]

    val yearly = records
      .groupBy($"ASMSerial", $"ASMMetric", year($"timestamp") as "year")
      .agg(
        min($"timestamp") as "start",
        max($"timestamp") as "end",
        count($"data") as "records",
        avg($"data") as "avg",
        max($"data") as "max",
        min($"data") as "min",
        stddev($"data") as "stddev"
      )
      .select(
        $"ASMSerial",
        $"ASMMetric",
        lit("year") as "duration",
        $"start",
        $"end",
        $"records",
        $"avg",
        $"max",
        $"min",
        $"stddev"
      )
      .dropDuplicates

    val monthly = records
      .groupBy(
        $"ASMSerial",
        $"ASMMetric",
        year($"timestamp") as "year",
        month($"timestamp") as "month"
      )
      .agg(
        min($"timestamp") as "start",
        max($"timestamp") as "end",
        count($"data") as "records",
        avg($"data") as "avg",
        max($"data") as "max",
        min($"data") as "min",
        stddev($"data") as "stddev"
      )
      .select(
        $"ASMSerial",
        $"ASMMetric",
        lit("month") as "duration",
        $"start",
        $"end",
        $"records",
        $"avg",
        $"max",
        $"min",
        $"stddev"
      )
      .dropDuplicates

    val ASM_metric_window = Window.partitionBy($"ASMSerial", $"ASMMetric", $"window")

    val daily = records
      .withColumn("window", window($"timestamp", "1 day"))
      .select(
        $"ASMSerial",
        $"ASMMetric",
        lit("day") as "duration",
        min($"timestamp").over(ASM_metric_window) as "start",
        max($"timestamp").over(ASM_metric_window) as "end",
        count($"timestamp").over(ASM_metric_window) as "records",
        avg($"data").over(ASM_metric_window) as "avg",
        min($"data").over(ASM_metric_window) as "min",
        max($"data").over(ASM_metric_window) as "max",
        stddev($"data").over(ASM_metric_window) as "stddev"
      )
      .dropDuplicates

    yearly.write
      .mode("overwrite")
      .parquet(s"$tmpHDFS/asm.metrics-yearly")

    monthly.write
      .mode("overwrite")
      .parquet(s"$tmpHDFS/asm.metrics-monthly")

    daily.write
      .mode("overwrite")
      .parquet(s"$tmpHDFS/asm.metrics-daily")
  }

  def createBreakdowns(spark: SparkSession): Unit = {
    import spark.implicits._

    val yearly = spark.read
      .parquet(s"$tmpHDFS/asm.metrics-yearly")

    val monthly = spark.read
      .parquet(s"$tmpHDFS/asm.metrics-monthly")

    val daily = spark.read
      .parquet(s"$tmpHDFS/asm.metrics-daily")

    val aggItemUDF = udf {
      (
      start: Timestamp,
      end: Timestamp,
      records: Long,
      avg: Double,
      max: Double,
      min: Double,
      stddev: Double
    ) => aggItem(start, end, records, avg, max, min, stddev)
    }

    val monthlyList = monthly
      .withColumn("year", year($"start"))
      .select($"ASMSerial", $"ASMMetric", $"year",
        collect_list(
          aggItemUDF($"start", $"end", $"records", $"avg", $"max", $"min", $"stddev")
        )
          .over(Window
            .partitionBy($"ASMSerial", $"ASMMetric", $"year")
            .orderBy($"start")) as "list")
      .groupBy($"ASMSerial", $"ASMMetric", $"year")
      .agg(max($"list") as "breakdown")

    val yearlyBreakdown = yearly
      .withColumn("year", year($"start"))
      .join(monthlyList, Seq("ASMSerial", "ASMMetric", "year"))

    val dailyList = daily
      .withColumn("year", year($"start"))
      .withColumn("month", month($"start"))
      .select(
        $"ASMSerial",
        $"ASMMetric",
        $"year",
        $"month",
        collect_list(aggItemUDF(
          $"start",
          $"end",
          $"records",
          $"avg",
          $"max",
          $"min",
          $"stddev"
        ))
          .over(Window
            .partitionBy($"ASMSerial", $"ASMMetric", $"year", $"month")
            .orderBy($"start")) as "list"
      )
      .groupBy($"ASMSerial", $"ASMMetric", $"year", $"month")
      .agg(max($"list") as "breakdown")

    val monthlyBreakdown = monthly
      .withColumn("year", year($"start"))
      .withColumn("month", month($"start"))
      .join(dailyList, Seq("ASMSerial", "ASMMetric", "year", "month"))

    yearlyBreakdown.write
      .mode("overwrite")
      .parquet(s"$tmpHDFS/asm.metrics-yearly-breakdown")

    monthlyBreakdown.write
      .mode("overwrite")
      .parquet(s"$tmpHDFS/asm.metrics-monthly-breakdown")
  }

  def jsonify(spark: SparkSession): Unit = {
    spark.read
      .parquet(s"$tmpHDFS/asm.metrics-yearly-breakdown")
      .write
      .mode("overwrite")
      .partitionBy("ASMSerial", "ASMMetric", "year")
      .json(s"$tmpDest")

    spark.read
      .parquet(s"$tmpHDFS/asm.metrics-monthly-breakdown")
      .drop("duration")
      .write
      .mode("append")
      .partitionBy("ASMSerial", "ASMMetric", "year", "month")
      .json(s"$tmpDest")
  }
}
