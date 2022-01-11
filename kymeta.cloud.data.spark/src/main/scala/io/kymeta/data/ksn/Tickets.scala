package io.kymeta.data.ksn

import scala.concurrent.duration.SECONDS
import org.apache.spark.sql.{ DataFrame, SparkSession }
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{ LongType, TimestampType }
import com.twosigma.flint.timeseries._
import org.apache.spark.sql.expressions.Window

object Tickets {
  def loadActivityTimeBreakdown(spark: SparkSession): DataFrame = {
    import spark.implicits._

    val activities = spark.read.parquet("wasbs://rawactivitylogs@kymetabigdata.blob.core.windows.net/activityLogs.parquet")

    // When the ticket was "created"
    val addRecords = activities
      .filter($"Action" === "addticket")
      .select($"EntityName" as "Ticket", $"Timestamp" as "time")

    // Ticket status update records
    val updateRecords = activities
      .filter($"Action" === "updateticket" && $"FieldName" === "status")
      .select($"EntityName" as "Ticket", $"Timestamp" as "time", $"OldValue", $"NewValue")

    val addsTS = TimeSeriesRDD.fromDF(dataFrame = addRecords)(isSorted = false, timeUnit = SECONDS)
    val updatesTS = TimeSeriesRDD.fromDF(dataFrame = updateRecords)(isSorted = false, timeUnit = SECONDS)

    // "addticket" records do not record the Status that the ticket is created in
    // Impute this using updates - the "OldValue" from oldest update record
    val addsImputed = addsTS
      .futureLeftJoin(right = updatesTS, key = Seq("Ticket"), tolerance = "100d")
      .toDF
      .select(
        $"Ticket",
        $"time" / lit(1000000000) cast TimestampType as "time",
        lit(null) as "OldValue",
        $"OldValue" as "NewValue"
      )

    addsImputed
      .union(updateRecords) // add and update records
      .withColumn("time_long", $"time" cast LongType)
      .select(
        $"Ticket",
        $"OldValue" as "Status",
        $"time_long" - lag($"time_long", 1).over(Window.partitionBy($"Ticket").orderBy($"time_long")) as "seconds"
      ) // for each ticket, calculate the time difference between consecutive records
      .na.drop // null values resulting from "created" records (no "duration" for creating tickets)
      .groupBy($"Ticket")
      .pivot("Status") // Make each status a column
      .sum("seconds")
      .na.fill(0) // default duration is 0 seconds
      .select(
        $"Ticket",
        $"open",
        $"pending",
        $"waiting_on_3rd_party",
        $"waiting_on_customer",
        $"resolved",
        $"closed"
      )
  }
}
