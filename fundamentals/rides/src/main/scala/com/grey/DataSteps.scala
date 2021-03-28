package com.grey

import java.io.File
import java.nio.file.Paths
import java.sql.Date

import com.grey.database.{DataSetUp, TableVariables}
import com.grey.environment.LocalSettings
import com.grey.pre.{DataStructure, DataWrite}
import com.grey.source.{DataRead, DataUnload}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession}
import org.apache.spark.sql.types.{DataType, StructType}
import org.joda.time.DateTime

import scala.collection.parallel.immutable.ParSeq
import scala.util.Try
import scala.util.control.Exception


/**
  *
  * @param spark: A SparkSession instance
  */
class DataSteps(spark: SparkSession) {

  private val localSettings = new LocalSettings()
  private val dataUnload = new DataUnload(spark = spark)
  private val dataRead = new DataRead(spark = spark)
  private val dataWrite = new DataWrite()
  private val dataSetUp = new DataSetUp()

  /**
    *
    * @param listOfDates : List of dates
    */
  def dataSteps(listOfDates: List[DateTime], filterDate: Date): Unit = {


    // Table
    val tableVariables = new TableVariables()
    val create: Try[Boolean] = new com.grey.libraries.mysql.CreateTable()
      .createTable(databaseString = "mysql.flow", tableVariables = tableVariables.tableVariables())
    if (create.isFailure) {
      sys.error(create.failed.get.getMessage)
    }


    // The schema of the data in question
    val schemaProperties: Try[RDD[String]] = Exception.allCatch.withTry(
      spark.sparkContext.textFile(localSettings.resourcesDirectory + "schemaOfSource.json")
    )


    // The StructType form of the schema
    val schema: StructType = if (schemaProperties.isSuccess) {
      DataType.fromJson(schemaProperties.get.collect.mkString("")).asInstanceOf[StructType]
    } else {
      sys.error(schemaProperties.failed.get.getMessage)
    }


    // Per time period: The host stores the data as month files
    val arraysOfFileObjects: ParSeq[Array[File]] = listOfDates.par.map{ dateTime =>

      // The directory into which the data of the date in question should be deposited (directoryName) and
      // the name to assign to the data file (fileString).  Note that fileString includes the path name.
      val directoryName: String = Paths.get(localSettings.dataDirectory, dateTime.toString("yyyy")).toString
      val fileString = directoryName + localSettings.localSeparator + dateTime.toString("MM") + ".json"

      // Unload
      val unload: Try[String] = dataUnload.dataUnload(
        dateTime = dateTime, directoryName = directoryName, fileString = fileString)

      // Read
      val read: Try[DataFrame] = if (unload.isSuccess) {
        dataRead.dataRead(dateTime = dateTime, fileString = fileString, schema = schema)
      } else {
        sys.error(unload.failed.get.getMessage)
      }

      // Structure
      val minimal: Dataset[Row] = new DataStructure(spark = spark)
        .dataStructure(data = read.get, filterDate = filterDate)

      // Write
      dataWrite.dataWrite(data = minimal, name = dateTime.toString("yyyyMM"))

    }


    // Set-up data for upload
    val fileObjects: Array[File] = arraysOfFileObjects.reduceRight( _ union _ )
    val setUp = dataSetUp.dataSetUp(fileObjects = fileObjects)


    // ... Upload
    // Replace this message with the upload function
    setUp.get.foreach{path =>
      println("uploading %s next".format(path.toString))
    }


  }


}
