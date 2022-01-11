package io.kymeta.data.asm.jobs

import io.kymeta.data.asm.Kiru
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.lit

object CompactKiru {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  def run(spark: SparkSession): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    val dataframe = Kiru
      .listASMSerials()
      .map(serial => spark.read // Load each ASMSerial individually
        .format("json")
        .schema(Kiru.schema)
        .option("mode", "DROPMALFORMED")
        .option("multiLine", true)
        .load(Kiru.wasbsRaw + serial)
        .withColumn("ASMSerial", lit(serial))) // Add ASMSerial column
      .reduce(_.union(_)) // Union all dataframes
      .repartition(4)
      .write
      .mode("overwrite")
      .parquet(Kiru.wasbsParquet)
  }
}
