import pureconfig._
import pureconfig.generic.auto._
import org.apache.spark.sql.SparkSession
import pureconfig.error.ConfigReaderFailures
import cats.effect.IO


case class SparkConfig(
                        appName: String,
                        master: String,
                        driverMemory: String,
                        executorMemory: String,
                        executorCores: Int
                      )

object ConfigValidator {
  def validateConfig(): IO[SparkConfig] = IO {
    // Load configuration from application.conf with environment variable overrides
    ConfigSource.default.at("spark").load[SparkConfig]
  }.flatMap {
    case Right(config) => IO.pure(config)
    case Left(errors: ConfigReaderFailures) =>
      IO.raiseError(new RuntimeException(s"Configuration errors: ${errors.toList.mkString(", ")}"))
  }
}

object SparkSessionBuilder {
  def createSparkSession(): IO[SparkSession] = {
    ConfigValidator.validateConfig().flatMap { config =>
      IO {
        SparkSession.builder()
          .appName(config.appName)
          .master(config.master)
          .config("spark.driver.memory", config.driverMemory)
          .config("spark.executor.memory", config.executorMemory)
          .config("spark.executor.cores", config.executorCores.toString)
          .getOrCreate()
      }
    }
  }
}
