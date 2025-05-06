package vertx.AI.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for retrieving application configuration using Vert.x ConfigRetriever.
 * <p>
 * Loads configuration from a local JSON file (`config.json`) and environment variables.
 */
public class ConfigService {

  private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
  private final ConfigRetriever configRetriever;

  /**
   * Constructs the {@code ConfigService} and sets up the configuration retriever
   * to load from both a JSON config file and environment variables.
   *
   * @param vertx the Vert.x instance used to initialize the config retriever
   */
  public ConfigService(Vertx vertx) {
    ConfigStoreOptions fileStore = new ConfigStoreOptions()
      .setType("file")
      .setFormat("json")
      .setConfig(new JsonObject().put("path", "config.json"));

    ConfigStoreOptions envStore = new ConfigStoreOptions().setType("env");

    ConfigRetrieverOptions options = new ConfigRetrieverOptions()
      .addStore(fileStore)
      .addStore(envStore);

    this.configRetriever = ConfigRetriever.create(vertx, options);
  }

  /**
   * Asynchronously retrieves the application configuration as a {@link JsonObject}.
   * Logs an error if the configuration cannot be loaded.
   *
   * @return a {@code Future} that completes with the loaded configuration
   */
  public Future<JsonObject> getConfig() {
    return configRetriever.getConfig().onFailure(err -> logger.error("Failed to load configuration", err));
  }

}
