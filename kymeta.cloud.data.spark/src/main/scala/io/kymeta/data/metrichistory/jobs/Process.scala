package io.kymeta.data.metrichistory.jobs

import com.microsoft.azure.storage.blob.ListBlobItem
import io.kymeta.data.https2wasbs
import io.kymeta.data.metrichistory.metrichistory.blobContainer
import io.kymeta.data.metrichistory.metrichistory.rawMetricsContainer
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.min

import scala.collection.JavaConverters._

object Process {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    // 1. Load to_process/*
    val to_process =
      spark.read
        .parquet("wasbs://rawmetrichistory@kymetabigdata.blob.core.windows.net/to_process")
        .distinct()

    // 2. Load min(RowKey)
    val minDF =
      spark.read
        .parquet(https2wasbs(
          blobContainer
          .listBlobs("data/")
          .asScala
          .min(Ordering.String.on[ListBlobItem](_.getUri.getPath.split("/")(3)))
          .getStorageUri
          .getPrimaryUri
        ).toString)

    // 3. new file name (new minimum RowKey)
    val newDF = to_process.except(minDF)
    val newDFName = newDF.agg(min('RowKey)).head.getString(0)
    newDF.write.parquet(s"wasbs://rawmetrichistory@kymetabigdata.blob.core.windows.net/data/$newDFName")

    // 4. Empty to_process/
    val toDelete = blobContainer
      .listBlobs("to_process/")
      .asScala
      .map(_.getUri.getPath.stripPrefix(s"/$rawMetricsContainer/"))

    toDelete.map(blobContainer.getBlockBlobReference(_).deleteIfExists())
  }
}
