package io.kymeta.data.reports.noc.jobs

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{ DataFrame, SparkSession }

object ResolutionByAccount {
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

    val accounts = spark.read.parquet("wasbs://access@kymetabigdata.blob.core.windows.net/ksn.accounts")
      .filter(!$"Name".rlike(".*((KGS)|(kgs)).*")) // Exclude KGS accounts
      .withColumnRenamed("Id", "AccountId")

    val resolutionTimeByAccount = tickets
      .withColumn("ResolutionTime", (unix_timestamp('ModifiedOn) - unix_timestamp('CreatedOn)))
      .select('Id, 'AccountId, 'ResolutionTime)
      .groupBy('AccountId)
      .agg(sum('ResolutionTime / 86400).as("Total"))
      .join(accounts, Seq("AccountId"))
      .select('AccountId, 'Name, 'Total as "Total Days")
      .sort($"Total".desc)

    resolutionTimeByAccount
      .coalesce(1)
      .write
      .mode("overwrite")
      .json(s"$tmpDest/resolution-time-by-account")
  }
}
