package io.kymeta.data.asm

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.{ CloudBlobClient, CloudBlobContainer }
import io.kymeta.data.{ kymetabigdataConnectionString, wasbsPath }
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ DataFrame, SparkSession }

import scala.collection.JavaConverters._

object Kiru {
  // https://wiki.kymetacorp.com/display/DPTENG/Kiru+Data+File
  val fullSchema = StructType(
    StructField("antenna-serial-number", StringType, true) ::
      StructField("asm-serial-number", StringType, true) ::
      StructField("customer", StringType, true) ::
      StructField("serial-number", StringType, true) ::
      StructField("status", LongType, true) ::
      StructField("testsuites", ArrayType(StructType(
        StructField("failures", LongType, true) ::
          StructField("name", StringType, true) ::
          StructField("start-time", StringType, true) ::
          StructField("stop-time", StringType, true) ::
          StructField("tests", LongType, true) ::
          StructField("testsuite", ArrayType(StructType(
            StructField("failures", LongType, true) ::
              StructField("name", StringType, true) ::
              StructField("status", LongType, true) ::
              StructField("testcase", ArrayType(StructType(
                StructField("classname", StringType, true) ::
                  StructField("data", StructType(
                    StructField("message", StringType, true) ::
                      StructField("status", LongType, true) ::
                      StructField("values", ArrayType(DoubleType), true) ::
                      Nil
                  ), true) ::
                  StructField("message", StringType, true) ::
                  StructField("name", StringType, true) ::
                  StructField("status", LongType, true) ::
                  StructField("time", DoubleType, true) ::
                  Nil
              )), true) ::
              StructField("tests", LongType, true) ::
              StructField("time", DoubleType, true) ::
              Nil
          )), true) ::
          StructField("time", DoubleType, true) ::
          Nil
      )), true) ::
      Nil
  )

  // Without testsuite details
  val schema = StructType(
    StructField("antenna-serial-number", StringType, true) ::
      StructField("asm-serial-number", StringType, true) ::
      StructField("customer", StringType, true) ::
      StructField("serial-number", StringType, true) ::
      StructField("status", LongType, true) ::
      StructField("testsuites", ArrayType(StructType(
        StructField("failures", LongType, true) ::
          StructField("name", StringType, true) ::
          StructField("start-time", StringType, true) ::
          StructField("stop-time", StringType, true) ::
          StructField("tests", LongType, true) ::
          StructField("time", DoubleType, true) ::
          Nil
      )), true) ::
      Nil
  )

  val container = "rawasmkiru"

  private val account: CloudStorageAccount = CloudStorageAccount.parse(kymetabigdataConnectionString)
  private val blobClient: CloudBlobClient = account.createCloudBlobClient()
  private val blobContainer: CloudBlobContainer = blobClient.getContainerReference(container)

  val wasbsRaw = wasbsPath(container)
  val wasbsParquet = wasbsPath("access", "asm.kiru")

  def listASMSerials(): Iterable[String] = blobContainer
    .listBlobs
    .asScala
    .map(_.getUri.getPath.split("/")(2))

  def load(spark: SparkSession): DataFrame = spark.read.parquet(wasbsParquet)
}
