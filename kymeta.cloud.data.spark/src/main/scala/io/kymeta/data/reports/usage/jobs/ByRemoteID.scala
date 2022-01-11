package io.kymeta.data.reports.usage.jobs

import java.sql.{ Date, Timestamp }

import scala.language.postfixOps

import sys.process._

import org.apache.log4j.{ Level, LogManager }

import org.apache.spark.sql.{ Column, DataFrame, SparkSession }
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ DoubleType, LongType, TimestampType }

import io.kymeta.data.metrichistory.metrichistory.maxTicks
import io.kymeta.data.prefixSI

object ByRemoteID {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    val log = LogManager.getRootLogger

    val has_all_confs =
      Set(
        "spark.kymeta.reports.usage.in.records",
        "spark.kymeta.reports.usage.in.terminals",
        "spark.kymeta.reports.usage.in.metrics",
        "spark.kymeta.reports.usage.in.terminalProps",
        "spark.kymeta.reports.usage.in.remotes"
      ).map(spark.conf.getAll.keySet.contains(_)) == Set(true)

    if (!has_all_confs) {
      log.error("Not all confs set")
      return
    }

    createJoinedRecords(spark)
    val monthly = getMonthly(spark)
    val daily = getDaily(spark)

    val dailyList = daily
      .withColumn("year", year($"start"))
      .withColumn("month", month($"start"))
      .select(
        $"ModemSerial",
        $"year",
        $"month",
        collect_list(
          struct(
            $"start",
            $"end",
            $"records",
            $"used",
            prefixSI($"used") as "SI"
          )
        )
          .over(Window
            .partitionBy($"ModemSerial", $"year", $"month")
            .orderBy($"start")) as "list"
      )
      .groupBy($"ModemSerial", $"year", $"month")
      .agg(max($"list") as "breakdown")

    val dailyBreakdown = monthly
      .withColumn("year", year($"start"))
      .withColumn("month", month($"start"))
      .join(dailyList, Seq("ModemSerial", "year", "month"))

    dailyBreakdown
      .join(getRemotes(spark), Seq("ModemSerial"))
      .select(
        $"RemoteId",
        $"RemoteName",
        $"RemoteSerial",
        $"year",
        $"month",
        $"start",
        $"end",
        $"records",
        $"used",
        prefixSI($"used") as "SI",
        $"breakdown"
      )
      .write
      .mode("overwrite")
      .partitionBy("RemoteId", "year", "month")
      .json(tmpDest)
  }

  def getRecords(spark: SparkSession): DataFrame = {
    import spark.implicits._

    spark.read
      .parquet(spark.conf.get("spark.kymeta.reports.usage.in.records"))
      .drop("Timestamp", "State1", "State2")
      .withColumn("_tmp", split($"PartitionKey", "\\."))
      .select(
        $"_tmp".getItem(0).as("TerminalId"),
        $"_tmp".getItem(1).as("MetricId"),
        $"_tmp".getItem(2).as("TerminalPropertyId"),
        (lit(maxTicks) - $"RowKey".cast(LongType)).as("timestamp"),
        $"Data",
        $"Type"
      )
  }

  def getTerminals(spark: SparkSession): DataFrame = {
    spark
      .read
      .parquet(spark.conf.get("spark.kymeta.reports.usage.in.terminals"))
      .withColumnRenamed("id", "TerminalId")
  }

  def getMetrics(spark: SparkSession): DataFrame = {
    spark
      .read
      .parquet(spark.conf.get("spark.kymeta.reports.usage.in.metrics"))
      .withColumnRenamed("id", "MetricId")
  }

  def getTerminalProps(spark: SparkSession): DataFrame = {
    import spark.implicits._

    spark
      .read
      .json(spark.conf.get("spark.kymeta.reports.usage.in.terminalProps"))
      .withColumn("Property", explode($"Properties"))
      .select(
        $"id" as "TP-TerminalId",
        $"SN" as "TP-SN",
        $"Name" as "TP-Name",
        $"Property.Id" as "TerminalPropertyId",
        $"Property.Key" as "TP-Key",
        $"Property.Value" as "TP-Value",
        $"Property.Description" as "TP-Description"
      )
  }

  def getRemotes(spark: SparkSession): DataFrame = {
    import spark.implicits._

    spark
      .read
      .parquet(spark.conf.get("spark.kymeta.reports.usage.in.remotes"))
      .filter($"ModemSerial".isNotNull)
      .select(
        $"Id" as "RemoteId",
        $"Name" as "RemoteName",
        $"ModemSerial"
      )
  }

  def createJoinedRecords(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val records = getRecords(spark)
    val terminals = getTerminals(spark)
    val metrics = getMetrics(spark)
    val terminalProps = getTerminalProps(spark)

    records
      .join(terminals, Seq("TerminalId"))
      .drop("Type", "Status")
      .withColumnRenamed("Name", "TerminalName")
      .join(metrics, Seq("MetricId"))
      .drop("Type", "Status", "AggregationType", "Description")
      .withColumnRenamed("Name", "MetricName")
      .join(terminalProps, Seq("TerminalPropertyId"))
      .filter($"IntId" === 1054 || $"IntId" === 1055) // upload/download
      .select(
        $"timestamp" cast TimestampType as "timestamp",
        $"SN" as "ModemSerial",
        $"IntId" as "MetricIntId",
        $"Data",
        split($"TP-Description", ":")(1) as "SSPP"
      )
      .filter(!$"SSPP".contains("NMS_SSPP")) // remove NMS_SSPP traffic
      .write
      .parquet(s"$tmpHDFS/joined.parquet") // Manually create local cache

    spark.read.parquet(s"$tmpHDFS/joined.parquet")
  }

  def getJoinedRecords(spark: SparkSession): DataFrame = {
    spark.read.parquet(s"$tmpHDFS/joined.parquet")
  }

  def getMonthly(spark: SparkSession): DataFrame = {
    import spark.implicits._

    getJoinedRecords(spark)
      .groupBy(
        $"ModemSerial",
        year($"timestamp") as "year",
        month($"timestamp") as "month"
      )
      .agg(
        min($"timestamp") as "start",
        max($"timestamp") as "end",
        count($"timestamp") as "records",
        sum($"Data") as "used"
      )
  }

  def getDaily(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val perSNDailyWindow = Window
      .partitionBy($"ModemSerial", $"window")

    getJoinedRecords(spark)
      .withColumn("window", window($"timestamp", "1 day"))
      .select(
        $"ModemSerial",
        min($"timestamp").over(perSNDailyWindow) as "start",
        max($"timestamp").over(perSNDailyWindow) as "end",
        count($"timestamp").over(perSNDailyWindow) as "records",
        sum($"Data").over(perSNDailyWindow) as "used"
      )
      .dropDuplicates
  }

  def jsonify(spark: SparkSession): Unit = {
    spark.read
      .parquet(s"$tmpHDFS/asm.metrics-yearly-breakdown")
      .write
      .mode("overwrite")
      .partitionBy("ASMSerial", "ASMMetric", "year")
      .json(s"$tmpDest")
  }
}
