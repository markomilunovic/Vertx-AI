package vertx.AI.verticle;

import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import vertx.AI.config.OpenAIConfigDefaults;
import vertx.AI.service.FileServiceInterface;
import vertx.AI.constants.EventBusAddresses;
import vertx.AI.service.OpenAIServiceInterface;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * {@code DocumentIndexVerticle} is responsible for initializing the embedding store ingestor
 * and indexing documents for retrieval-augmented generation (RAG).
 * <p>
 * It performs the following tasks on startup:
 * <ul>
 *   <li>Loads embedding model configuration and initializes the {@link EmbeddingStoreIngestor}</li>
 *   <li>Indexes all documents in the configured directory using {@link FileServiceInterface}</li>
 *   <li>Registers an event bus consumer on {@link EventBusAddresses#RAG_INDEX} to support dynamic file uploads</li>
 * </ul>
 * <p>
 * This verticle is deployed as part of the RAG pipeline to enable OpenAI to generate
 * contextually enriched responses based on indexed knowledge.
 */
public class DocumentIndexVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(DocumentIndexVerticle.class);

  private final FileServiceInterface fileService;
  private final OpenAIServiceInterface openAIService;

  private EmbeddingStoreIngestor embeddingStoreIngestor;

  /**
   * Constructs the {@code DocumentIndexVerticle}.
   *
   * @param fileService     service for managing and indexing files
   * @param openAIService   service for initializing embedding-related components
   */
  public DocumentIndexVerticle(FileServiceInterface fileService, OpenAIServiceInterface openAIService) {
    this.fileService = fileService;
    this.openAIService = openAIService;
  }

  /**
   * Starts the verticle by initializing the embedding store ingestor and indexing existing documents.
   * Also registers a listener on the event bus to re-index documents when new files are uploaded.
   *
   * @param startPromise promise to be completed when the verticle is started
   */
  @Override
  public void start(Promise<Void> startPromise) {
    JsonObject config = config();

    String apiKey = config.getString("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      startPromise.fail("Missing OpenAI API Key.");
    }

    JsonObject embeddingModelConfig = config.getJsonObject("embeddingModel", new JsonObject());
    String embeddingModelName = embeddingModelConfig.getString("modelName", OpenAIConfigDefaults.EMBEDDING_MODEL_NAME);

    String documentsDirectory = config.getString("documentsDirectory", OpenAIConfigDefaults.DOCUMENTS_DIRECTORY).trim();
    if (documentsDirectory.isEmpty()) {
      logger.warn("Documents directory is empty in config.json. Using default: documents/");
      documentsDirectory = OpenAIConfigDefaults.DOCUMENTS_DIRECTORY;
    }

    String finalDocumentsDirectory = documentsDirectory;

    openAIService.initializeEmbeddingStoreIngestor(apiKey, embeddingModelName)
      .onSuccess(ingestor -> logger.info("EmbeddingStoreIngestor initialized."))
      .compose(ingestor -> {
        this.embeddingStoreIngestor = ingestor;
        return fileService.indexDocuments(finalDocumentsDirectory, ingestor);
      })
      .onSuccess(v -> {
        logger.info("Documents indexed successfully.");
        vertx.eventBus().consumer(EventBusAddresses.RAG_INDEX, message -> {
          String newFilePath = (String) message.body();
          logger.info("New document uploaded. Re-indexing: {}", newFilePath);
          fileService.indexFileIfNotIndexed(newFilePath, embeddingStoreIngestor)
            .onSuccess(r -> logger.info("Re-indexed document successfully: {}", newFilePath))
            .onFailure(err -> logger.error("Failed to re-index document: {}", newFilePath, err));
        });
        startPromise.complete();
      })
      .onFailure(err -> {
        logger.error("Failed to initialize or index documents", err);
        startPromise.fail(err);
      });
  }
}
