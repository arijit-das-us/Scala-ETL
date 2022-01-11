package io.kymeta.data.metrichistory.jobs

import io.kymeta.data.metrichistory.metrichistory.blobContainer
import com.microsoft.azure.storage.blob.ListBlobItem
import io.kymeta.data.https2wasbs
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

object DumpTable {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    // 1. Load to_process/* and min(RowKey)
    var pathsBuf = ListBuffer.empty[String]

    // Grab the latest (minimum rowkey) parquet file
    pathsBuf += https2wasbs(
      blobContainer
      .listBlobs("data/")
      .asScala
      .min(Ordering.String.on[ListBlobItem](_.getUri.getPath.split("/")(3)))
      .getStorageUri
      .getPrimaryUri
    ).toString

    // files to be processed (dedup)
    if (!blobContainer.listBlobs("to_process/").asScala.isEmpty) {
      pathsBuf += "wasbs://rawmetrichistory@kymetabigdata.blob.core.windows.net/to_process"
    }

    val minRowKey = spark.read.parquet(pathsBuf: _*).agg(min('RowKey)).head.getString(0)

    // 3. Write metadata/minRowKey
    blobContainer.getBlockBlobReference("metadata/minRowKey").uploadText(minRowKey)
  }
}
