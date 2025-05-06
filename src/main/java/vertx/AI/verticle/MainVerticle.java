package vertx.AI.verticle;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import vertx.AI.config.ConfigService;
import vertx.AI.service.FileServiceInterface;
import vertx.AI.service.OpenAIServiceInterface;
import vertx.AI.service.impl.FileService;
import vertx.AI.service.impl.OpenAIService;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MainVerticle is the core bootstrap verticle responsible for initializing
 * all essential components of the RAG application.
 *
 * <p>On startup, it performs the following steps:
 * <ol>
 *   <li>Loads application configuration using {@link ConfigService}</li>
 *   <li>Initializes a {@link MongoDbEmbeddingStore} for vector-based retrieval</li>
 *   <li>Sets up the {@link FileService} for handling document ingestion and indexing</li>
 *   <li>Sets up the {@link OpenAIService} for integrating with OpenAI's chat and embedding APIs</li>
 *   <li>Deploys the following dependent verticles:
 *     <ul>
 *       <li>{@link DocumentIndexVerticle} - handles initial and dynamic document ingestion</li>
 *       <li>{@link OpenAIVerticle} - manages OpenAI chat interaction (streaming and non-streaming)</li>
 *       <li>{@link HttpServerVerticle} - exposes the REST endpoints for document upload and chat</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>If any step fails (configuration, MongoDB setup, verticle deployment), it logs the error and fails the startup promise.
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  /**
   * Starts the Verticle. Initializes services, MongoDB embedding store,
   * and deploys the supporting verticles required for the full application.
   *
   * @param startPromise promise that is completed when the startup process finishes
   */
  @Override
  public void start(Promise<Void> startPromise) {
    logger.info("Starting application... Loading configuration...");

    new ConfigService(vertx).getConfig().onSuccess(config -> {
      DeploymentOptions options = new DeploymentOptions().setConfig(config);

      vertx.executeBlocking(() -> {
        logger.info("Initializing MongoDB Embedding Store...");

        String connectionString = config.getString("mongoConnectionString");
        String dbName = config.getString("mongoDbName");
        String collectionName = config.getString("mongoCollectionName");
        String indexName = config.getString("mongoIndexName");

        MongoClient mongoClient = MongoClients.create(connectionString);

        MongoDbEmbeddingStore embeddingStore = MongoDbEmbeddingStore.builder()
          .fromClient(mongoClient)
          .databaseName(dbName)
          .collectionName(collectionName)
          .indexName(indexName)
          .createIndex(false)
          .build();

        logger.info("MongoDB Embedding Store initialized.");

        MongoDatabase database = mongoClient.getDatabase(dbName);
        MongoCollection<BsonDocument> collection = database.getCollection(collectionName, BsonDocument.class);

        FileServiceInterface fileService = new FileService(vertx, collection);
        OpenAIServiceInterface openAIService = new OpenAIService(vertx, embeddingStore);

        return new Object[] { fileService, openAIService };
      }).compose(services -> {
        FileServiceInterface fileService = (FileServiceInterface) services[0];
        OpenAIServiceInterface openAIService = (OpenAIServiceInterface) services[1];

        return vertx.deployVerticle(new DocumentIndexVerticle(fileService, openAIService), options)
          .compose(docIndexId -> {
            logger.info("DocumentIndexVerticle deployed successfully.");
            return vertx.deployVerticle(new OpenAIVerticle(openAIService), options);
          })
          .compose(openAiId -> {
            logger.info("OpenAIVerticle deployed successfully.");
            return vertx.deployVerticle(new HttpServerVerticle(fileService), options);
          });
      }).onSuccess(httpServerId -> {
        logger.info("HttpServerVerticle deployed successfully.");
        startPromise.complete();
      }).onFailure(err -> {
        logger.error("Failed to deploy verticles sequentially", err);
        startPromise.fail(err);
      });
    }).onFailure(err -> {
      logger.error("Failed to load configuration", err);
      startPromise.fail(err);
    });
  }
}
