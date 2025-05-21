import cats.effect.IO
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.ml.feature.{StandardScaler, StandardScalerModel, VectorAssembler}

object DataLoader {
  val featureCols = Array("danceability", "energy", "valence", "tempo", "acousticness", "instrumentalness", "liveness")

  def loadAndScaleData(spark: SparkSession): IO[DataFrame] = IO.blocking {
    val dfRaw = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv("src/main/resources/spotifydataset.csv")
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