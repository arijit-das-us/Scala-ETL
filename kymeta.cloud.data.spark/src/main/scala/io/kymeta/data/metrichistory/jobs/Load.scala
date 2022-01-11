package io.kymeta.data.metrichistory.jobs

import io.kymeta.data.metrichistory.metrichistory.maxTicks
import io.kymeta.data._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.LongType

object Load {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    val data = spark.read.parquet(
      wasbsRawMetricHistory,
      wasbsRawMetricHistoryIncremental
    )
    val terminals = spark.read.parquet(wasbsRawTerminals)
    val metrics = spark.read.parquet(wasbsRawMetrics)

    val splitted = data
      .drop("Timestamp", "State1", "State2")
      .withColumn("_tmp", split('PartitionKey, "\\."))
      .select(
        '_tmp.getItem(0).as("TerminalId"),
        '_tmp.getItem(1).as("MetricId"),
        (lit(maxTicks) - 'RowKey.cast(LongType)).as("timestamp"),
        'Data, 'Type
      )

    val joined = splitted
      .join(terminals, splitted("TerminalId") === terminals("id"))
      .drop("Status", "id", "TerminalId", "Type")
      .withColumnRenamed("Name", "TerminalName")
      .withColumnRenamed("SN", "ModemSerial")
      .join(metrics, splitted("MetricId") === metrics("id"))
      .drop("MetricId", "id")
      .withColumnRenamed("Name", "MetricName")
      .withColumnRenamed("IntId", "MetricInt")
      .withColumnRenamed("Type", "MetricType")
      .write
      .mode("Overwrite")
      .parquet(wasbsAccessRemoteMetrics)
  }
}
