import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.apache.spark.ml.feature.{StandardScaler, StandardScalerModel, VectorAssembler}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.types.DoubleType

object Main extends App {


  // Step 1: Initialize Spark
  val spark: SparkSession = SparkSession.builder()
    .appName("Spotify Track Similarity")
    .master("local[*]")
    .getOrCreate()

  import spark.implicits._

  // Step 2: Load the data
  val dfRaw: DataFrame = spark.read
    .option("header", "true")
    .option("inferSchema", "true")
    .csv("src/main/resources/spotifydataset.csv")
    .na.drop()

  // Step 3: Choose feature columns (adjust as needed)
  val featureCols = Array("danceability", "energy", "valence", "tempo", "acousticness", "instrumentalness", "liveness")

  // Step 4: Assemble feature vector
  val assembler: VectorAssembler = new VectorAssembler()
    .setInputCols(featureCols)
    .setOutputCol("features")

  val dfFeatures: DataFrame = assembler.transform(dfRaw)

  // Optional: Normalize features
  val scaler: StandardScaler = new StandardScaler()
    .setInputCol("features")
    .setOutputCol("scaledFeatures")
    .setWithStd(true)
    .setWithMean(true)

  val scalerModel: StandardScalerModel = scaler.fit(dfFeatures)
  val dfScaled: DataFrame = scalerModel.transform(dfFeatures)

  // Step 5: Choose a query track by name
  val queryTrackName = "One Last Time" // Change this
  val queryVector: Option[Vector] = dfScaled
    .filter($"track_name" === queryTrackName)
    .select("scaledFeatures")
    .collect()
    .headOption
    .map(_.getAs[Vector]("scaledFeatures"))

  queryVector match {
    case Some(queryVec) =>
      // Step 6: Define cosine similarity UDF
      val cosineSimilarity: (Vector => Double) = (vec: Vector) => {
        val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
        val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
        val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)
        if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
      }

      val cosineUDF: UserDefinedFunction = F.udf((vec: Vector) => {
        val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
        val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
        val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)
        if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
      })

      // Step 7: Compute similarity and show top matches
      val dfSimilar = dfScaled
        .withColumn("similarity", cosineUDF($"scaledFeatures"))
        .filter($"track_name" =!= queryTrackName)
        .orderBy(F.desc("similarity"))
        .select("track_name", "artist_name", "similarity")

      dfSimilar.show(10, truncate = false)

    case None =>
      println(s"Track '$queryTrackName' not found in dataset.")
  }
}
