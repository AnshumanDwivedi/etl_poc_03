package com.jcdecaux.datacorp.spark.storage.repository

import com.jcdecaux.datacorp.spark.annotation.InterfaceStability
import com.jcdecaux.datacorp.spark.enums.Storage
import com.jcdecaux.datacorp.spark.internal.{Logging, SchemaConverter}
import com.jcdecaux.datacorp.spark.storage.Condition
import com.jcdecaux.datacorp.spark.storage.connector.{Connector, DBConnector, FileConnector}
import org.apache.spark.sql.{Dataset, Encoder}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

@InterfaceStability.Evolving
class SparkRepository[DataType <: Product : ClassTag : TypeTag] extends Repository[DataType] with Logging {

  private[this] var connector: Connector = _

  def getStorage: Storage = connector.storage

  /**
    * Set the connector of this spark repository
    *
    * @param connector [[com.jcdecaux.datacorp.spark.storage.connector.Connector]] an user defined connector
    * @return
    */
  def setConnector(connector: Connector): this.type = {
    this.connector = connector
    this
  }

  def getConnector: Connector = this.connector

  def partitionBy(columns: String*): this.type = {
    connector match {
      case c: FileConnector => c.partitionBy(columns: _*)
      case _ =>
    }
    this
  }

  /**
    * Find data by giving a single condition
    *
    * @param condition a [[Condition]]
    * @param encoder   : implicit encoder of Spark
    * @return
    */
  override def findBy(condition: Condition)(implicit encoder: Encoder[DataType]): Dataset[DataType] = {
    this.findBy(Set(condition))

  }

  /**
    * Find data by giving a set of conditions
    *
    * @param conditions Set of [[com.jcdecaux.datacorp.spark.storage.Condition]]
    * @param encoder    implicit encoder of Spark
    * @return
    */
  override def findBy(conditions: Set[Condition])(implicit encoder: Encoder[DataType]): Dataset[DataType] = {
    import com.jcdecaux.datacorp.spark.util.FilterImplicits._

    val data = if (conditions.nonEmpty && !conditions.toSqlRequest.isEmpty) {
      connector.read().filter(conditions.toSqlRequest)
    } else {
      connector.read()
    }

    SchemaConverter.fromDF[DataType](data)
  }

  /**
    * Retrieve all data
    *
    * @param encoder : implicit encoder of Spark
    * @return
    */
  override def findAll()(implicit encoder: Encoder[DataType]): Dataset[DataType] = {
    SchemaConverter.fromDF[DataType](connector.read())
  }

  /**
    * Save a [[Dataset]] into a data persistence store
    *
    * @param data    data to be saved
    * @param encoder : implicit encoder of Spark
    * @return
    */
  override def save(data: Dataset[DataType], suffix: Option[String] = None)(implicit encoder: Encoder[DataType]): SparkRepository.this.type = {

    connector match {
      case c: DBConnector => c.create(data.toDF(), suffix)
      case _ => log.info("Current class has no create method. Save directly the dataset")
    }
    connector.write(SchemaConverter.toDF(data), suffix)
    this
  }
}
