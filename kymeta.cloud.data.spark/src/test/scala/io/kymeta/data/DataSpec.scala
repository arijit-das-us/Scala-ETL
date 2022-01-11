package io.kymeta.data

import org.scalatest._

class DataSpec extends FlatSpec with Matchers {
  val testURI = Seq(
    Map(
      "wasbs" -> "wasbs://test@kymetabigdata.blob.core.windows.net/",
      "https" -> "https://kymetabigdata.blob.core.windows.net/test/"
    ),
    Map(
      "wasbs" -> "wasbs://test@kymetabigdata.blob.core.windows.net/data/3155378974472885099",
      "https" -> "https://kymetabigdata.blob.core.windows.net/test/data/3155378974472885099"
    )
  )

  "wasbs converter" should "convert wasbs:// to https://" in {
    testURI.map(testCase =>
      wasbs2https(testCase("wasbs")) should be(testCase("https")))
  }

  it should "convert https:// to wasbs://" in {
    testURI.map(testCase =>
      https2wasbs(testCase("https")) should be(testCase("wasbs")))
  }
}
