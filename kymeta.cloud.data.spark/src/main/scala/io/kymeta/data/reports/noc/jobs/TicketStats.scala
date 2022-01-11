package io.kymeta.data.reports.noc.jobs

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{ DataFrame, SparkSession }

object TicketStats {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def openTickets(spark: SparkSession)(tickets: DataFrame): DataFrame = {
    import spark.implicits._

    val openTickets = tickets.filter($"Status" =!= "closed")

    // All tickets
    var ret = tickets.withColumn("Open#All", lit(openTickets.count))

    val breakdown = openTickets
      .groupBy('Priority)
      .agg(count('Number))

    var urgentPrioSum: Long = 0
    var highPrioSum: Long = 0
    var lowPrioSum: Long = 0

    for (l <- breakdown.collect()) {
      l.getString(0) match {
        case "urgent" => urgentPrioSum += l.getLong(1)
        case "high" => highPrioSum += l.getLong(1)
        case "normal" => lowPrioSum += l.getLong(1)
        case "low" => lowPrioSum += l.getLong(1)
        case _ => lowPrioSum += l.getLong(1)
      }
    }

    ret = ret.withColumn("Open#SeverityLevel1", lit(urgentPrioSum))
    ret = ret.withColumn("Open#SeverityLevel2", lit(highPrioSum))
    ret = ret.withColumn("Open#SeverityLevel3", lit(lowPrioSum))

    return ret
  }

  def closedTickets(spark: SparkSession)(tickets: DataFrame): DataFrame = {
    import spark.implicits._

    val weeklyClosed = tickets.filter('Status === "closed")
      .withColumn("Year", year('ModifiedOn))
      .withColumn("Week", weekofyear('ModifiedOn))
      .groupBy("Year", "Week")
      .agg(count("Number"))
      .withColumn("Year-Week", concat('Year, lit("-"), 'Week)).sort('Year, 'Week)

    val weeklyOut = weeklyClosed.sort('Year.desc, 'Week.desc).limit(2).collect()

    // Monthly Closed Tickets
    val monthlyClosed = tickets.filter('Status === "closed")
      .withColumn("Year", year('ModifiedOn))
      .withColumn("Month", month('ModifiedOn))
      .groupBy("Year", "Month")
      .agg(count("Number"))
      .withColumn("Year-Month", concat('Year, lit("-"), 'Month)).sort('Year, 'Month)

    return tickets
      .withColumn("Closed#CurrentWeek", lit(weeklyOut(0)(2)))
      .withColumn("Closed#PreviousWeek", lit(weeklyOut(1)(2)));
  }

  def resolutionTimes(spark: SparkSession)(tickets: DataFrame): DataFrame = {
    import spark.implicits._

    val closedTickets = tickets
      .filter('Status === "closed")
      .filter('Type =!= "rma")
      .withColumn("ResolutionTime_Days", (unix_timestamp('ModifiedOn, "PST") - unix_timestamp('CreatedOn, "PST")) / (60 * 60 * 24))
      .sort('ModifiedOn.desc)

    val avgResolutionTime =
      BigDecimal(closedTickets
        .agg(avg('ResolutionTime_Days))
        .first
        .getDouble(0))
        .setScale(1, BigDecimal.RoundingMode.HALF_DOWN).toFloat

    val lastN = Seq(15, 50, 200)
      .map(closedTickets
        .limit(_)
        .agg(avg('ResolutionTime_Days))
        .first
        .getDouble(0))
      .map(BigDecimal(_).setScale(1, BigDecimal.RoundingMode.HALF_DOWN).toFloat)

    return tickets
      .withColumn("ResolutionTime#All", lit(avgResolutionTime))
      .withColumn("ResolutionTime#Last#15", lit(lastN(0)))
      .withColumn("ResolutionTime#Last#50", lit(lastN(1)))
      .withColumn("ResolutionTime#Last#200", lit(lastN(2)))
  }

  def rmaResolutionTimes(spark: SparkSession)(tickets: DataFrame): DataFrame = {
    import spark.implicits._

    val rmaTickets = tickets
      .filter('Status === "closed")
      .filter('Type === "rma")
      .withColumn("ResolutionTime_Days", (unix_timestamp('ModifiedOn, "PST") - unix_timestamp('CreatedOn, "PST")) / (60 * 60 * 24))
      .sort('ModifiedOn.desc)

    val closedRMATickets = rmaTickets.filter('Status === "closed")

    val avgRMAResolutionTime =
      BigDecimal(closedRMATickets
        .agg(avg('ResolutionTime_Days))
        .first
        .getDouble(0))
        .setScale(1, BigDecimal.RoundingMode.HALF_DOWN).toFloat

    val lastNRMA = Seq(15, 50, 200)
      .map(closedRMATickets.limit(_)
        .agg(avg('ResolutionTime_Days))
        .first.getDouble(0))
      .map(BigDecimal(_).setScale(1, BigDecimal.RoundingMode.HALF_DOWN).toFloat)

    return tickets
      .withColumn("RMAResolutionTime#All", lit(avgRMAResolutionTime))
      .withColumn("RMAResolutionTime#last#15", lit(lastNRMA(0)))
      .withColumn("RMAResolutionTime#last#50", lit(lastNRMA(1)))
      .withColumn("RMAResolutionTime#last#200", lit(lastNRMA(2)))
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    val ignoreTickets = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.tags")
      .filter('Name === "ignore")
      .select('EntityId)

    val allTickets = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.tickets")

    val tickets = allTickets
      .join(ignoreTickets, allTickets("Id") === ignoreTickets("EntityId"), "left_anti")

    val finalTickets = tickets
      .transform(openTickets(spark))
      .transform(closedTickets(spark))
      .transform(resolutionTimes(spark))
      .transform(rmaResolutionTimes(spark))
      .limit(1)
      .select(
        map(lit("All"), $"Open#All",
          lit("SeverityLevel1"), $"Open#SeverityLevel1",
          lit("SeverityLevel2"), $"Open#SeverityLevel2",
          lit("SeverityLevel3"), $"Open#SeverityLevel3") as "Open",
        map(lit("CurrentWeek"), $"Closed#CurrentWeek",
          lit("PrevWeek"), $"Closed#PreviousWeek") as "Closed",
        map(lit("All"), $"ResolutionTime#All",
          lit("Last#15"), $"ResolutionTime#last#15",
          lit("Last#50"), $"ResolutionTime#last#50",
          lit("Last#200"), $"ResolutionTime#last#200") as "ResolutionTime",
        map(lit("All"), $"RMAResolutionTime#All",
          lit("Last#15"), $"RMAResolutionTime#last#15",
          lit("Last#50"), $"RMAResolutionTime#last#50",
          lit("Last#200"), $"RMAResolutionTime#last#200") as "RMAResolutionTime"
      )

    // z.show(finalTickets)
    finalTickets
      .write.mode("overwrite")
      .json(s"$tmpDest/ticket-stats")
  }
}
