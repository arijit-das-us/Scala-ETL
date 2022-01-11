package io.kymeta.data.chamber

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.{ CloudBlobClient, CloudBlobContainer }
import io.kymeta.data.{ kymetabigdataConnectionString, wasbsPath }
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ DataFrame, SparkSession }

import scala.collection.JavaConverters._

object Chamber {

  val container = "farchamber"

  private val account: CloudStorageAccount = CloudStorageAccount.parse(kymetabigdataConnectionString)
  private val blobClient: CloudBlobClient = account.createCloudBlobClient()
  val blobContainer: CloudBlobContainer = blobClient.getContainerReference(container)

  val wasbsRaw = wasbsPath(container)
  val wasbsParquet = wasbsPath("access", "farchamber")

  val schema2 = StructType(
    StructField("jira", StringType, true) ::
      StructField("Tx", StructType(
        StructField("pattern_type", StringType, true) ::
          StructField("config_to_use", StringType, true) ::
          StructField("web_api_parameters", StringType, true) ::
          StructField("wave_equation_parameters", StructType(
            StructField("binary", StringType, true) ::
              StructField("bin_threshold", StringType, true) ::
              StructField("phi", DoubleType, true) ::
              StructField("index_of_refraction", StringType, true) ::
              StructField("lin_pol_angle", DoubleType, true) ::
              StructField("phase", StringType, true) ::
              StructField("theta", DoubleType, true) ::
              StructField("freq", DoubleType, true) ::
              StructField("mod_power", StringType, true) ::
              Nil
          ), true) ::
          StructField("settling_time", DoubleType, true) ::
          StructField("pattern_config_file", StringType, true) ::
          StructField("stop_frequency", DoubleType, true) ::
          StructField("freq_step_ghz", DoubleType, true) ::
          StructField("start_frequency", DoubleType, true) ::
          StructField("PPF_file", StringType, true) :: Nil
      ), true) ::
      StructField("start_time", StringType, true) ::
      StructField("Rx", StructType(
        StructField("pattern_type", StringType, true) ::
          StructField("config_to_use", StringType, true) ::
          StructField("web_api_parameters", StringType, true) ::
          StructField("wave_equation_parameters", StructType(
            StructField("binary", StringType, true) ::
              StructField("bin_threshold", StringType, true) ::
              StructField("phi", DoubleType, true) ::
              StructField("index_of_refraction", StringType, true) ::
              StructField("lin_pol_angle", DoubleType, true) ::
              StructField("phase", StringType, true) ::
              StructField("theta", DoubleType, true) ::
              StructField("freq", DoubleType, true) ::
              StructField("mod_power", StringType, true) ::
              Nil
          ), true) ::
          StructField("settling_time", DoubleType, true) ::
          StructField("pattern_config_file", StringType, true) ::
          StructField("stop_frequency", DoubleType, true) ::
          StructField("freq_step_ghz", DoubleType, true) ::
          StructField("start_frequency", DoubleType, true) ::
          StructField("PPF_file", StringType, true) :: Nil
      ), true) ::
      StructField("band", StringType, true) ::
      StructField("measurement_type", StringType, true) ::
      StructField("mtennalib_version", StringType, true) ::
      StructField("test_chamber", StringType, true) ::
      StructField("hertzscan_version", StringType, true) ::
      StructField("auxiliary_angle", StringType, true) ::
      StructField("testplan", StringType, true) ::
      StructField("asm_firmware_version", StringType, true) ::
      StructField("mtenna_build_number", StringType, true) ::
      StructField("comments", StringType, true) ::
      StructField("data_in_file", StringType, true) ::
      StructField("orbit_sw_version", StructType(
        StructField("build_state", StringType, true) ::
          StructField("module_version", StringType, true) ::
          StructField("build_number", StringType, true) ::
          StructField("server_version", StringType, true) ::
          StructField("product_version", StringType, true) ::
          Nil
      ), true) ::
      StructField("stop_time", StringType, true) ::
      StructField("axis_mapping", StructType(
        StructField("Auxiliary", StringType, true) ::
          StructField("Primary", StringType, true) ::
          StructField("Tertiary", StringType, true) ::
          StructField("Secondary", StringType, true) ::
          Nil
      ), true) ::
      StructField("antenna_datafile_pairs", StructType(
        StructField("Rx", StringType, true) ::
          Nil
      ), true) ::
      StructField("File", StringType, true) ::
      StructField("data", ArrayType(StructType(
        StructField("Point", IntegerType, true) ::
          StructField("Tertiary", DoubleType, true) ::
          StructField("Secondary", DoubleType, true) ::
          StructField("Frequency", DoubleType, true) ::
          StructField("Primary", DoubleType, true) ::
          StructField("Ch1_dB", DoubleType, true) ::
          StructField("Ch1_Deg", DoubleType, true) ::
          Nil
      ), true), true) ::
      StructField("Date", TimestampType, true) ::
      Nil
  )

  val schema = StructType(
    StructField("jira", StringType, true) ::
      StructField("start_time", StringType, true) ::
      StructField("band", StringType, true) ::
      StructField("measurement_type", StringType, true) ::
      StructField("mtennalib_version", StringType, true) ::
      StructField("test_chamber", StringType, true) ::
      StructField("hertzscan_version", StringType, true) ::
      StructField("auxiliary_angle", StringType, true) ::
      StructField("testplan", StringType, true) ::
      StructField("asm_firmware_version", StringType, true) ::
      StructField("mtenna_build_number", StringType, true) ::
      StructField("comments", StringType, true) ::
      StructField("data_in_file", StringType, true) ::
      StructField("stop_time", StringType, true) ::
      StructField("File", StringType, true) ::
      StructField("Date", DateType, true) ::
      StructField("Tx", StructType(
        StructField("pattern_type", StringType, true) ::
          StructField("config_to_use", StringType, true) ::
          StructField("web_api_parameters", StringType, true) ::
          StructField("settling_time", DoubleType, true) ::
          StructField("pattern_config_file", StringType, true) ::
          StructField("stop_frequency", DoubleType, true) ::
          StructField("freq_step_ghz", DoubleType, true) ::
          StructField("start_frequency", DoubleType, true) ::
          StructField("PPF_file", StringType, true) ::
          StructField("wave_equation_parameters", StructType(
            StructField("binary", StringType, true) ::
              StructField("bin_threshold", StringType, true) ::
              StructField("phi", DoubleType, true) ::
              StructField("index_of_refraction", StringType, true) ::
              StructField("lin_pol_angle", DoubleType, true) ::
              StructField("phase", StringType, true) ::
              StructField("theta", DoubleType, true) ::
              StructField("freq", DoubleType, true) ::
              StructField("mod_power", StringType, true) ::
              Nil
          ), true) :: Nil
      ), true) ::
      StructField("Rx", StructType(
        StructField("pattern_type", StringType, true) ::
          StructField("config_to_use", StringType, true) ::
          StructField("web_api_parameters", StringType, true) ::
          StructField("settling_time", DoubleType, true) ::
          StructField("pattern_config_file", StringType, true) ::
          StructField("stop_frequency", DoubleType, true) ::
          StructField("freq_step_ghz", DoubleType, true) ::
          StructField("start_frequency", DoubleType, true) ::
          StructField("PPF_file", StringType, true) ::
          StructField("wave_equation_parameters", StructType(
            StructField("binary", StringType, true) ::
              StructField("bin_threshold", StringType, true) ::
              StructField("phi", DoubleType, true) ::
              StructField("index_of_refraction", StringType, true) ::
              StructField("lin_pol_angle", DoubleType, true) ::
              StructField("phase", StringType, true) ::
              StructField("theta", DoubleType, true) ::
              StructField("freq", DoubleType, true) ::
              StructField("mod_power", StringType, true) ::
              Nil
          ), true) :: Nil
      ), true) ::
      StructField("orbit_sw_version", StructType(
        StructField("build_state", StringType, true) ::
          StructField("module_version", StringType, true) ::
          StructField("build_number", StringType, true) ::
          StructField("server_version", StringType, true) ::
          StructField("product_version", StringType, true) ::
          Nil
      ), true) ::
      StructField("axis_mapping", StructType(
        StructField("Auxiliary", StringType, true) ::
          StructField("Primary", StringType, true) ::
          StructField("Tertiary", StringType, true) ::
          StructField("Secondary", StringType, true) ::
          Nil
      ), true) ::
      StructField("antenna_datafile_pairs", StructType(
        StructField("Rx", StringType, true) ::
          Nil
      ), true) ::
      StructField("data", ArrayType(StructType(
        StructField("Point", IntegerType, true) ::
          StructField("Tertiary", DoubleType, true) ::
          StructField("Secondary", DoubleType, true) ::
          StructField("Frequency", DoubleType, true) ::
          StructField("Primary", DoubleType, true) ::
          StructField("Ch1_dB", DoubleType, true) ::
          StructField("Ch1_Deg", DoubleType, true) ::
          Nil
      ), true), true) ::
      Nil
  )

  def listASMSerials(): Iterable[String] = blobContainer
    .listBlobs
    .asScala
    .map(_.getUri.getPath.split("/")(2))

  def load(spark: SparkSession): DataFrame = spark.read.parquet(wasbsParquet)

}