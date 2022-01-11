package io.kymeta.data.metrichistory

import io.kymeta.data.{ MetaStats, blobClient, wasbsPath }
import org.apache.spark.sql.{ DataFrame, SparkSession }

package object metrichistory {
  val rawMetricsContainer = "rawmetrichistory"
  val maxTicks = 3155378975999999999L

  val blobContainer = blobClient.getContainerReference(rawMetricsContainer)
  val wasbsParquet = wasbsPath("access", "remotemetrics")

  //val stat = MetaStats()
  def load(spark: SparkSession): DataFrame = spark.read.parquet(wasbsParquet)
}
