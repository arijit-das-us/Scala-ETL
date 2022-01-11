package io.kymeta.data

import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.functions._

package object device_checkin {
  def raw(spark: SparkSession): DataFrame =
    spark.read.parquet("wasbs://rawdevicemanifests@kymetabigdata.blob.core.windows.net/data.parquet")

  def cleaned(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val partition_schema = new StructType()
      .add($"Uuid".string)
      .add($"Type".string)
      .add($"Device".string)
      .add($"Label".string)
      .add($"FsType".string)
      .add($"Size".long)
      .add($"Used".long)
      .add($"MountPath".string)
      .add($"SoftwareVersion".string)

    raw(spark)
      .filter($"PartitionKey".startsWith("AAE") || $"PartitionKey".startsWith("TEST"))
      .withColumn("ap", from_json($"ActivePartition", partition_schema))
      .select(
        $"PartitionKey" as "ASMSerial",
        $"Timestamp",
        $"ap.Type" as "Type",
        $"ap.Device" as "Device",
        $"ap.Label" as "Label",
        $"ap.FsType" as "FsType",
        $"ap.Size" as "Size",
        $"ap.Used" as "Used",
        $"ap.Size" - $"ap.Used" as "Free",
        $"ap.MountPath" as "MountPath",
        $"ap.SoftwareVersion" as "SoftwareVersion"
      )
  }

  def latest(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val latest_timestamp = cleaned(spark)
      .groupBy("ASMSerial")
      .agg(max($"Timestamp") as "Timestamp")

    cleaned(spark).join(latest_timestamp, Seq("ASMSerial", "Timestamp"))
  }

  def by_asmserial(spark: SparkSession, asmSerial: String): DataFrame =
    cleaned(spark).filter(col("ASMSerial") === asmSerial).sort(col("Timestamp"))
}