package io.kymeta.data.asm.jobs

import io.kymeta.data.asm
import io.kymeta.data.asm.Metrics
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.types.DoubleType

object CompactMetrics {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    var dataframe = Metrics
      .listASMSerials()
      .map(serial => spark.read // Load each ASMSerial individually
        .format("json")
        .schema(Metrics.schema)
        // .option("mode", "DROPMALFORMED")
        .load(Metrics.wasbsRaw + serial)
        .withColumn("ASMSerial", lit(serial))) // Add ASMSerial column
      .reduce(_.union(_)) // Union all dataframes

    // Some column names need to be escaped
    dataframe = Metrics.renameColumns(dataframe, Metrics.colMapForward)

    // Some columns are loaded as StringType but really contain DoubleType data
    for (colName <- Metrics.colTypesToRename) {
      dataframe = dataframe.withColumn(colName._4, dataframe(colName._4) cast DoubleType)
    }

    // Write it out
    dataframe
      .repartition(4)
      .write
      .mode("overwrite")
      .parquet(Metrics.wasbsParquet)
  }
}
