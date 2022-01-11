package io.kymeta.data.asm

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.{ CloudBlobClient, CloudBlobContainer }
import io.kymeta.data.asm.Kiru.wasbsParquet
import io.kymeta.data.{ kymetabigdataConnectionString, wasbsPath }
import org.apache.spark.sql.{ DataFrame, SparkSession }

import scala.collection.JavaConverters._
import org.apache.spark.sql.types._

object POST {
  // https://wiki.kymetacorp.com/display/DPTENG/POST+%28Power+On+Self+Test%29+File
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
          //          StructField("testsuite", ArrayType(StructType(
          //              StructField("failures", LongType, true) ::
          //              StructField("name", LongType, true) ::
          //              StructField("status", LongType, true) ::
          //            Nil)), true) ::
          Nil
      )), true) ::
      Nil
  )

  val container = "rawasmpost"

  private val account: CloudStorageAccount = CloudStorageAccount.parse(kymetabigdataConnectionString)
  private val blobClient: CloudBlobClient = account.createCloudBlobClient()
  private val blobContainer: CloudBlobContainer = blobClient.getContainerReference(container)

  val wasbsRaw = wasbsPath(container)
  val wasbsParquet = wasbsPath("access", "asm.post")

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

  def load(spark: SparkSession): DataFrame = spark.read.parquet(wasbsParquet)
}
