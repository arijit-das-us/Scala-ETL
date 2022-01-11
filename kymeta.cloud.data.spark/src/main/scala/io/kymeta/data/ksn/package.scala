package io.kymeta.data

import com.microsoft.azure.storage.CloudStorageAccount
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }

package object ksn {
  def lastModified(blob: String): DateTime = {
    val account = CloudStorageAccount.parse(kymetabigdataConnectionString)
    val blobClient = account.createCloudBlobClient()
    val blobContainer = blobClient.getContainerReference("access")

    val tickets = blobContainer.getBlockBlobReference(blob)
    tickets.downloadAttributes()
    new DateTime(tickets.getProperties.getLastModified).withZone(DateTimeZone.forID("US/Pacific"))
  }

  def lastModifiedPretty(blob: String): String = {
    lastModified(blob).toString(DateTimeFormat.longDateTime)
  }
}
