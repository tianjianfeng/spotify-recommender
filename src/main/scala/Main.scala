import org.apache.spark.sql.{DataFrame, Row, SparkSession, functions => F}
import org.apache.spark.ml.feature.{StandardScaler, StandardScalerModel, VectorAssembler}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.types.DoubleType

object Main extends App {

  val spark: SparkSession = SparkSession.builder()
    .appName("Spotify Track Similarity")
    .master("local[*]")
    .getOrCreate()

  import spark.implicits._

  val dfRaw: DataFrame = spark.read
    .option("header", "true")
    .option("inferSchema", "true")
    .csv("src/main/resources/spotifydataset.csv")
    .na.drop()

  val featureCols = Array("danceability", "energy", "valence", "tempo", "acousticness", "instrumentalness", "liveness")

  def assembleAndScaleFeatures(df: DataFrame, inputCols: Array[String], spark: SparkSession): DataFrame = {
    val assembler = new VectorAssembler()
      .setInputCols(inputCols)
      .setOutputCol("features")

    val dfFeatures = assembler.transform(df)

    val scaler = new StandardScaler()
      .setInputCol("features")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)

    val scalerModel = scaler.fit(dfFeatures)
    scalerModel.transform(dfFeatures)
  }

  val dfScaled: DataFrame = assembleAndScaleFeatures(dfRaw, featureCols, spark)

  val queryTrackName = "One Last Time"
  val queryRow: Row = dfScaled
    .filter($"track_name" === queryTrackName)
    .select("artist_name", "genres", "scaledFeatures")
    .head()

  val queryArtist = queryRow.getAs[String]("artist_name")
  val queryGenre  = queryRow.getAs[String]("genres")
  val queryVec    = queryRow.getAs[Vector]("scaledFeatures")

  val cosineUDF: UserDefinedFunction = F.udf((vec: Vector) => {
    val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
    val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
    val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)
    if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
  })

  def recommend(df: DataFrame, filterMode: String, title: String): Unit = {
    val filteredDF = filterMode match {
      case "artist" => df.filter($"artist_name" === queryArtist && $"track_name" =!= queryTrackName)
      case "genre"  => df.filter($"genres" === queryGenre && $"track_name" =!= queryTrackName)
      case _         => df.filter($"track_name" =!= queryTrackName)
    }

    println(s"\n--- $title ---")
    val results = filteredDF
      .withColumn("similarity", cosineUDF($"scaledFeatures"))
      .orderBy(F.desc("similarity"))
      .select("track_name", "artist_name", "genres", "similarity")

    results.show(10, truncate = false)
  }

  recommend(dfScaled, "all", "Top Similar Tracks")
  recommend(dfScaled, "artist", "Tracks from Same Artist")
  recommend(dfScaled, "genre", "Tracks from Same Genre")
}
