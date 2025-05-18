import org.apache.spark.sql.{DataFrame, Dataset, Row, SparkSession, functions => F}
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
    val assembler: VectorAssembler = new VectorAssembler()
      .setInputCols(inputCols)
      .setOutputCol("features")

    val dfFeatures: DataFrame = assembler.transform(df)

    val scaler = new StandardScaler()
      .setInputCol("features")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)

    val scalerModel: StandardScalerModel = scaler.fit(dfFeatures)
    scalerModel.transform(dfFeatures)
  }

  def getRecommendations(
                          df: DataFrame,
                          queryTrackName: String,
                          mode: String, // "all", "artist", or "genre"
                          featureCols: Array[String]
                        )(implicit spark: SparkSession): DataFrame = {
    import spark.implicits._

    // Assemble + scale
    val dfScaled = assembleAndScaleFeatures(df, featureCols, spark)

    // Get query track row
    val queryRow = dfScaled
      .filter($"track_name" === queryTrackName)
      .select("artist_name", "genres", "scaledFeatures")
      .head

    val queryArtist = queryRow.getAs[String]("artist_name")
    val queryGenre  = queryRow.getAs[String]("genres")
    val queryVec    = queryRow.getAs[Vector]("scaledFeatures")

    // Precompute query norm once
    val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)

    val cosineUDF: UserDefinedFunction = F.udf((vec: Vector) => {
      val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
      val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
      if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    })

    // Apply filtering based on mode
    val filteredDF = mode match {
      case "artist" => dfScaled.filter($"artist_name" === queryArtist && $"track_name" =!= queryTrackName)
      case "genre"  => dfScaled.filter($"genres" === queryGenre && $"track_name" =!= queryTrackName)
      case _        => dfScaled.filter($"track_name" =!= queryTrackName)
    }

    // Return top 10 similar tracks
    filteredDF
      .withColumn("similarity", cosineUDF($"scaledFeatures"))
      .orderBy(F.desc("similarity"))
      .select("track_name", "artist_name", "genres", "similarity")
      .limit(10)
  }

//  val queryTrack = "One Last Time"
//  val mode = "genre" // "all", "artist", or "genre"
//
//  val topRecommendations = getRecommendations(dfRaw, queryTrack, mode, featureCols)(spark)
//  topRecommendations.show(truncate = false)

}
