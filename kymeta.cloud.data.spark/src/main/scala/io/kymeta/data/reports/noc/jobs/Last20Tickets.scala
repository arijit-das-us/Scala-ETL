package io.kymeta.data.reports.noc.jobs

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{ DataFrame, SparkSession }

object Last20Tickets {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    val ignoreTickets = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.tags")
      .filter('Name === "ignore")
      .select('EntityId)

    val allTickets = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.tickets")

    val tickets = allTickets
      .join(ignoreTickets, allTickets("Id") === ignoreTickets("EntityId"), "left_anti")

    val createdTickets = tickets
      .orderBy('CreatedOn.desc)
      .limit(20)
      .select('Number, 'Priority, 'Subject, 'CreatedOn)

    val closedTickets = tickets
      .filter('Status === "closed")
      .filter('Type =!= "rma")
      .withColumn("ResolutionTime_Days", (unix_timestamp('ModifiedOn, "PST") - unix_timestamp('CreatedOn, "PST")) / (60 * 60 * 24))
      .sort('ModifiedOn.desc)
      .select('Number, 'Priority, 'Subject, 'ResolutionTime_Days)
      .limit(20)

    createdTickets
      .coalesce(1)
      .write
      .mode("overwrite")
      .json(s"$tmpDest/tickets/last-20-created")

    closedTickets
      .coalesce(1)
      .write
      .mode("overwrite")
      .json(s"$tmpDest/tickets/last-20-closed")
  }
}
