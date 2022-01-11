package io.kymeta

import java.net.URI

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.CloudBlobClient

package object data {
  val kymetabigdataConnectionString = "DefaultEndpointsProtocol=https;AccountName=kymetabigdata;AccountKey=cQ/5mwr5Dt2PQhzei4EyuLn7pYDR/NTm8nRVXfzBlPyLlmobpDgAzygh48612dTf5q4s9R0uLXB+E4UHvuFvNw==;EndpointSuffix=core.windows.net"

  def wasbsPath(container: String, path: String = "") = s"wasbs://$container@kymetabigdata.blob.core.windows.net/$path"

  def wasbsRawTerminals = wasbsPath("rawterminalhistory", "terminals")
  def wasbsRawMetrics = wasbsPath("rawterminalhistory", "metrics")
  def wasbsRawMetricHistory = wasbsPath("rawmetrichistory", "data")
  def wasbsRawMetricHistoryIncremental = wasbsPath("rawmetrichistory", "data/*")

  def wasbsAccessRemoteMetrics = wasbsPath("access", "remotemetrics")

  def https2wasbs(uri: URI): URI = {
    // https://kymetabigdata.blob.core.windows.net/test/data/3155378974472885099
    // wasbs://test@kymetabigdata.blob.core.windows.net/data/3155378974472885099

    val userinfo = uri.getPath.split("/")(1)
    val path = uri.getPath.stripPrefix(s"/$userinfo")

    new URI(
      "wasbs",
      userinfo,
      uri.getHost,
      uri.getPort,
      path,
      uri.getQuery,
      uri.getFragment
    )
  }

  def https2wasbs(uri: String): String = https2wasbs(new URI(uri)).toString

  def wasbs2https(uri: URI): URI = {
    // wasbs://test@kymetabigdata.blob.core.windows.net/data/3155378974472885099
    // https://kymetabigdata.blob.core.windows.net/test/data/3155378974472885099

    new URI(
      "https",
      null,
      uri.getHost,
      uri.getPort,
      s"/${uri.getUserInfo}${uri.getPath}",
      uri.getQuery,
      uri.getFragment
    )
  }

  def wasbs2https(uri: String): String = wasbs2https(new URI(uri)).toString

  val blobAccount: CloudStorageAccount = CloudStorageAccount.parse(kymetabigdataConnectionString)
  val blobClient: CloudBlobClient = blobAccount.createCloudBlobClient()

  case class MetaStats(datapoints: Long, rawSize: Long, compressedSize: Long)

  val prefixSI = org.apache.spark.sql.functions.udf((s: Double) => {
    val kilo = 1000.0
    val mega = 1000 * kilo
    val giga = 1000 * mega
    val tera = 1000 * giga

    s match {
      case x if (x >= tera) => f"${x / tera}%3.2f TB"
      case x if (x < tera && x >= giga) => f"${x / giga}%3.2f GB"
      case x if (x < giga && x >= mega) => f"${x / mega}%3.2f MB"
      case x if (x < mega && x >= kilo) => f"${x / kilo}%3.2f KB"
      case _ => s"${s} B"
    }
  })
}
