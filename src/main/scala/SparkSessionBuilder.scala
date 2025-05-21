import pureconfig._
import pureconfig.generic.auto._
import org.apache.spark.sql.SparkSession
import pureconfig.error.ConfigReaderFailures

case class SparkConfig(
  appName: String,
  master: String,
  driverMemory: String,
  executorMemory: String,
  executorCores: Int
)

object ConfigValidator {
  def validateConfig(): Either[ConfigReaderFailures, SparkConfig] = {
    // Load configuration from application.conf with environment variable overrides
    ConfigSource.default.at("spark").load[SparkConfig]
  }
}

object SparkSessionBuilder {
  def createSparkSession(): SparkSession = {
    val config = ConfigValidator.validateConfig() match {
      case Right(validConfig) => validConfig
      case Left(errors) =>
        throw new RuntimeException(s"Configuration errors: ${errors.toList.mkString(", ")}")
    }

    SparkSession.builder()
      .appName(config.appName)
      .master(config.master)
      .config("spark.driver.memory", config.driverMemory)
      .config("spark.executor.memory", config.executorMemory)
      .config("spark.executor.cores", config.executorCores.toString)
      .getOrCreate()
  }
}