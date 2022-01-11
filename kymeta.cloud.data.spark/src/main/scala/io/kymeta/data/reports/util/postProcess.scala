// improvements: gzip
// - gzip .json files
// - set Content-Encoding to gzip on blobs after upload

package io.kymeta.data.reports.util

import scala.language.postfixOps

import sys.process._

import java.io.{
  BufferedOutputStream,
  File,
  FileOutputStream,
  OutputStream,
  PrintWriter
}

import scala.io.Source

import org.apache.log4j.{ Level, LogManager, PropertyConfigurator }

import org.apache.spark.sql.SparkSession

object postProcess {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder.getOrCreate()

    run(spark)

    spark.stop()
  }

  // 1. HDFS to local file system
  // 2. Merge to one .json
  // 3. Rename to index.json
  // 4. azcopy to destination (we assume azcopy exists - it is on HDInsight)
  // -- set-content-type
  def run(spark: SparkSession): Unit = {
    import spark.implicits._

    val log = LogManager.getRootLogger

    // val localHDFS = "hdfs://mycluster/new_batch"
    // val localtmp = ("mktemp -d"!!).dropRight(1) // drop newline character
    // val azcopyDest = "https://kcsreports.blob.core.windows.net/reports-int"
    // val azcopyDestSAS = "?st=2019-02-28T21%3A05%3A47Z&se=2019-03-01T21%3A05%3A47Z&sp=rwl&sv=2015-04-05&sr=c&sig=ciu0bfWQhnDAgyc%2BW8oaZI2sFHTAvwISv7G7JyyTlt4%3D"

    // Make sure all requisite parameters are set
    //
    // TODO: find an idiomatic way to do this and include in the "kymeta job"
    // supertype
    val has_all_confs =
      Set(
        "spark.kymeta.reports.postprocess.input",
        "spark.kymeta.reports.postprocess.output",
        "spark.kymeta.reports.postprocess.output.sas"
      )
        .map(spark.conf.getAll.keySet.contains(_)) == Set(true)

    if (!has_all_confs) {
      log.error("Not all confs set")
      return
    }

    val localHDFS = spark.conf.get("spark.kymeta.reports.postprocess.input")
    val azcopyDest = spark.conf.get("spark.kymeta.reports.postprocess.output")
    val azcopyDestSAS = spark.conf.get("spark.kymeta.reports.postprocess.output.sas")

    val localtmp = ("mktemp -d"!!).dropRight(1) // drop newline character

    // HDFS to local file system
    val hdfsCopyToLocalCommand = s"""
hdfs dfs -copyToLocal ${localHDFS} ${localtmp}/
""".stripMargin.replaceAll("\n", " ").drop(1) // drop leading empty space

    log.warn(hdfsCopyToLocalCommand)

    if (0 != (hdfsCopyToLocalCommand!)) {
      // Failed to copy files from HDFS into local filesystem
      return

      // Looks like it's time we need a proper logging framework...
    }

    // Iterate through all directories that contain part-????-*.json
    def process(dir: String): Unit = {
      // Merge and "flatten" multiple jsonlines files to index.json
      val index = s"${dir}/index.json"

      val joined_json = new File(dir)
        .listFiles
        .filter(_.isFile)
        .filter(_.getName.startsWith("part-"))
        .filter(_.getName.endsWith(".json")) // Only interested in part-*.json files
        .map(_.getPath)
        .map(Source.fromFile(_).getLines) // At this point, we have Array of iterators
        .foldLeft(Iterator[String]())(_ ++ _) // iterators concatenated
        .mkString("[", ",", "]") // https://www.oreilly.com/library/view/scala-cookbook/9781449340292/ch10s30.html

      val indexWriter = new PrintWriter(
        new BufferedOutputStream(
          new FileOutputStream(index, /* append */ false)
        )
      )

      indexWriter.println(joined_json)
      indexWriter.close()

      // Delete all files that aren't index.json
      new File(dir)
        .listFiles
        .filter(_.isFile)
        .filter(_.getName != "index.json")
        .map(_.delete)
    }

    // Call process for every directory that has part-.*json file
    (s"find ${localtmp}" #| "grep -e part-.*json" #| "xargs dirname" #| "uniq"!!)
      .split("\n")
      .map(process _)

    // We're assuming azcopy exists on the namenode - this is the case with HDInsight

    // In the future on a different SPARK instance, it'd make sense to deploy
    // this as part of a runbook that installs azcopy before running these jobs
    val azcopyCommand = s"""
/usr/bin/azcopy
 --quiet
 --resume .
 --recursive
 --set-content-type application/json
 --source ${localtmp}
 --destination ${azcopyDest}
 --dest-sas ${azcopyDestSAS}
""".stripMargin.replaceAll("\n", " ").drop(1) // drop leading empty space

    log.warn(azcopyCommand)

    // azcopy to destination
    if (0 != (azcopyCommand!)) {
      // Failed to upload prepped files to blob storage
      return

      // Looks like it's time we need a proper logging framework...
    }
  }
}
