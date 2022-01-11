package io.kymeta.data.reports.noc.jobs

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{ DataFrame, SparkSession }

object TicketsByAccount {
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
      .select(
        $"Id" as "AccountId",
        $"Name" as "AccountName",
        $"Number" as "AccountNumber"
      )

    val byAccount = tickets
      .withColumn("ResolutionTime", unix_timestamp('ModifiedOn) - unix_timestamp('CreatedOn))
      .join(accounts, Seq("AccountId"))
      .select('AccountId, 'AccountName, 'Number, format_string("%.1f", ('ResolutionTime / 86400)) as "Days", 'Subject)
      .sort('Number.desc)

    byAccount
      .coalesce(1)
      .write
      .partitionBy("AccountId")
      .mode("overwrite")
      .json(s"$tmpDest/by-account")

    val accountList = byAccount
      .select($"AccountId", $"AccountName")
      .dropDuplicates
      .sort($"AccountName")

    accountList
      .coalesce(1)
      .write
      .mode("overwrite")
      .json(s"$tmpDest/by-account/list")
  }
}
