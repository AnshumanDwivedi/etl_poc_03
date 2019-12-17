package com.jcdecaux.setl.storage.connector

import java.io.File

import com.jcdecaux.setl.config.Properties
import com.jcdecaux.setl.{SparkSessionBuilder, TestObject}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.funsuite.AnyFunSuite

class JSONConnectorSuite extends AnyFunSuite {

  val path: String = "src/test/resources/test JSON"
  val testTable: Seq[TestObject] = Seq(
    TestObject(1, "p1", "c1", 1L),
    TestObject(2, "p2", "c2", 2L),
    TestObject(2, "p2", "c2", 2L),
    TestObject(2, "p2", "c2", 2L),
    TestObject(2, "p2", "c2", 2L),
    TestObject(3, "p3", "c3", 3L)
  )

  test("JSON connector IO") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    val connector = new JSONConnector(Map("path" -> path, "saveMode" -> "Overwrite"))

    import spark.implicits._

    connector.partitionBy("partition1").write(testTable.toDF())
    val df = connector.read()
    assert(df.count() === 6)
    assert(df.filter($"partition1" === 2).count() === 4)
    connector.delete()
  }

  test("test JSON connector with different file path") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    import spark.implicits._

    val path1: String = new File("src/test/resources/test_json").toURI.toString
    val path2: String = new File("src/test/resources/test_json").getPath

    val connector1 = new JSONConnector(Map[String, String]("path" -> path1, "saveMode" -> "Overwrite"))
    val connector2 = new JSONConnector(Map[String, String]("path" -> path2, "saveMode" -> "Overwrite"))

    connector1.write(testTable.toDF)
    val df = connector2.read()
    assert(df.count() === 6)
    connector1.delete()
  }

  test("IO with auxiliary JSONConnector constructor") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    import spark.implicits._

    val connector = new JSONConnector(Properties.jsonConfig)

    connector.write(testTable.toDF())
    connector.write(testTable.toDF())

    val df = connector.read()
    df.show()
    assert(df.count() === 12)
    connector.delete()
  }

  test("Test JSON Connector Suffix") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    import spark.implicits._

    val connector = new JSONConnector(Map("path" -> path, "saveMode" -> "Append"))

    connector.write(testTable.toDF(), Some("2"))
    connector.write(testTable.toDF(), Some("2"))
    connector.write(testTable.toDF(), Some("1"))
    connector.write(testTable.toDF(), Some("3"))

    val df = connector.read()
    df.show()
    assert(df.count() == 24)
    assert(df.filter($"partition1" === 1).count() === 4)
    assert(df.filter($"partition1" === 1).dropDuplicates().count() === 1)

    connector.delete()
    assertThrows[org.apache.spark.sql.AnalysisException](connector.read())
  }

  test("test JSON partition by") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    import spark.implicits._

    val dff: Dataset[TestObject] = Seq(
      TestObject(1, "p1", "c1", 1L),
      TestObject(2, "p2", "c2", 2L),
      TestObject(2, "p1", "c2", 2L),
      TestObject(3, "p3", "c3", 3L),
      TestObject(3, "p2", "c3", 3L),
      TestObject(3, "p3", "c3", 3L)
    ).toDS()

    val connector = new JSONConnector(Map[String, String]("path" -> path, "saveMode" -> "Overwrite"))
      .partitionBy("partition1", "partition2")

    // with partition, with suffix
    connector.write(dff.toDF, Some("1"))
    connector.write(dff.toDF, Some("2"))
    connector.dropUserDefinedSuffix(false)

    connector.read().show()
    assert(connector.read().count() === 12)
    assert(connector.read().columns.length === 5)
    connector.delete()

    // with partition without suffix
    connector.resetSuffix(true)
    connector.write(dff.toDF)
    assert(connector.read().count() === 6)
    assert(connector.read().columns.length === 4, "column suffix should not exists")
    connector.dropUserDefinedSuffix(true)
    assert(connector.read().columns.length === 4, "column suffix should not exists")
    connector.delete()
  }

  test("Complex JSON file") {
    /*
    TODO cannot run this test with the current guava version (21.0). This version is a dependency of embedded Cassandra
     IllegalAccessException will be thrown. You should try with version 15.0
     */

    // val connector = new JSONConnector(spark, Map("path" -> "src/test/resources/test-json.json", "saveMode" -> "Append", "multiLine" -> "true"))
    // connector.read().show()

  }

  test("JSONConnector should be able to write standard JSON format") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    import spark.implicits._

    val path1: String = new File("src/test/resources/standart_json_format").toURI.toString
    val connector1 = new JSONConnector(Map[String, String]("path" -> path1, "saveMode" -> "Overwrite"))
    connector1.writeStandardJSON(testTable.toDF)
    assert(connector1.readStandardJSON() === "[{\"partition1\":1,\"partition2\":\"p1\",\"clustering1\":\"c1\",\"value\":1},{\"partition1\":2,\"partition2\":\"p2\",\"clustering1\":\"c2\",\"value\":2},{\"partition1\":2,\"partition2\":\"p2\",\"clustering1\":\"c2\",\"value\":2},{\"partition1\":2,\"partition2\":\"p2\",\"clustering1\":\"c2\",\"value\":2},{\"partition1\":2,\"partition2\":\"p2\",\"clustering1\":\"c2\",\"value\":2},{\"partition1\":3,\"partition2\":\"p3\",\"clustering1\":\"c3\",\"value\":3}]")
    connector1.deleteStandardJSON()
  }

}