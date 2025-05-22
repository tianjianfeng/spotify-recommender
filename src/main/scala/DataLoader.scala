import cats.effect.IO
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.ml.feature.{StandardScaler, StandardScalerModel, VectorAssembler}
import pureconfig.ConfigSource
import pureconfig._
import pureconfig.generic.auto._

case class CsvConfig(path: String)

object DataLoader {
  val featureCols = Array("danceability", "energy", "valence", "tempo", "acousticness", "instrumentalness", "liveness")

  def loadAndScaleData(spark: SparkSession): IO[DataFrame] = IO.blocking {
    val csvConfig = ConfigSource.default.at("csv").loadOrThrow[CsvConfig]

    val dfRaw = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(csvConfig.path)
      .na.drop()

    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features")
    val dfFeatures = assembler.transform(dfRaw)

    val scaler = new StandardScaler()
      .setInputCol("features")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)
    val scalerModel: StandardScalerModel = scaler.fit(dfFeatures)
    scalerModel.transform(dfFeatures).cache()
  }
}