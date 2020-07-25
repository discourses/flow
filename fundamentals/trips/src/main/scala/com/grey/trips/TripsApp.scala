package com.grey.trips

import com.grey.trips.environment.{ConfigParameters, DataDirectories, LocalSettings}
import com.grey.trips.specific.DataTimes
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.joda.time.DateTime

import scala.collection.parallel.immutable.ParSeq
import scala.util.Try

object TripsApp {

  private val localSettings = new LocalSettings()
  private val configParameters = new ConfigParameters()
  private val dataTimes = new DataTimes()

  def main(args: Array[String]): Unit = {

    // Foremost, are the date strings and/or periods real Gregorian Calendar dates?
    // Presently, the dates are printed in InterfaceVariables.  The dates will be arguments of this app.
    val listOfDates: List[DateTime] = dataTimes.dataTimes()


    // Minimise Spark & Logger Information Outputs
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)


    // Spark Session Instance
    val spark: SparkSession = SparkSession.builder().appName("trips")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.warehouse.dir", localSettings.databaseDirectory)
      .enableHiveSupport()
      .config("spark.master", "local[*]")
      .getOrCreate()


    // Spark Context Level Logging
    spark.sparkContext.setLogLevel("ERROR")


    // Configurations
    // spark.conf.set("spark.speculation", value = false)
    spark.conf.set("spark.sql.shuffle.partitions", configParameters.nShufflePartitions.toString)
    spark.conf.set("spark.default.parallelism", configParameters.nParallelism.toString)
    spark.conf.set("spark.kryoserializer.buffer.max", "2048m")


    // Graphs Model Checkpoint Directory
    spark.sparkContext.setCheckpointDir("/tmp")


    // Prepare local directories
    val dataDirectories = new DataDirectories()
    val directories: ParSeq[Try[Boolean]] = List(localSettings.dataDirectory, localSettings.localWarehouse)
      .par.map( directory => dataDirectories.localDirectoryReset(directory) )


    // Hence
    if (directories.head.isSuccess) {
      val dataSteps = new DataSteps(spark)
      dataSteps.dataSteps(listOfDates)
    } else {
      // Superfluous
      sys.error(directories.head.failed.get.getMessage)
    }


  }

}