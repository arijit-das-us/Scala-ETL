package io.kymeta.data.chamber.jobs

import io.kymeta.data.chamber
import io.kymeta.data.chamber.Chamber
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.types.DoubleType
import scala.collection.JavaConverters._

object CompactFarfieldChamber {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    val dataframe = Chamber
      .listASMSerials()
      .map(jira => spark.read // Load each ASMSerial individually
        .format("json")
        .load(Chamber.wasbsRaw + jira))
      .reduce(_.union(_)) // Union all dataframes
      .write
      .mode("overwrite")
      .parquet(Chamber.wasbsParquet)

  }
}
