package vertx.AI.service.impl;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import vertx.AI.config.OpenAIModelConfig;
import vertx.AI.rag.CustomQueryTransformer;
import vertx.AI.service.OpenAIServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Implementation of {@link OpenAIServiceInterface} responsible for initializing and managing
 * Langchain4j-based OpenAI models, embedding services, and retrieval-augmented generation components.
 */
public class OpenAIService implements OpenAIServiceInterface {

  private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
  private final Vertx vertx;
  private final MongoDbEmbeddingStore embeddingStore;
  private OpenAiStreamingChatModel streamingChatModel;
  private OpenAiChatModel chatModel;
  private EmbeddingModel embeddingModel;
  private ContentRetriever contentRetriever;
  private EmbeddingStoreIngestor embeddingStoreIngestor;
  private RetrievalAugmentor retrievalAugmentor;

  /**
   * Constructs a new {@code OpenAIService} with Vert.x and a MongoDB-based embedding store.
   *
   * @param vertx          the Vert.x instance used for async operations
   * @param embeddingStore the MongoDB-based embedding store
   */
  public OpenAIService(Vertx vertx, MongoDbEmbeddingStore embeddingStore) {
    this.vertx = vertx;
    this.embeddingStore = embeddingStore;
  }

  /**
   * Initializes the OpenAI streaming chat model with the specified configuration.
   *
   * @param config the OpenAI model configuration
   * @return a {@link Future} that completes with the initialized {@link OpenAiStreamingChatModel}
   */
  @Override
  public Future<OpenAiStreamingChatModel> initializeStreamingChatModel(OpenAIModelConfig config) {
    logger.info("Initializing OpenAI Streaming Chat Model...");

    return vertx.executeBlocking(() -> {
      streamingChatModel = OpenAiStreamingChatModel.builder()
        .apiKey(config.getApiKey())
        .modelName(config.getModelName())
        .temperature(config.getTemperature())
        .topP(config.getTopP())
        .presencePenalty(config.getPresencePenalty())
        .frequencyPenalty(config.getFrequencyPenalty())
        .maxTokens(config.getMaxTokens())
        .responseFormat(config.getResponseFormat())
        .stop(config.getStop())
        .logRequests(true)
        .logResponses(true)
        .build();
      logger.info("OpenAI Streaming model initialized successfully.");
      return streamingChatModel;
    });
  }

  /**
   * Initializes the non-streaming OpenAI chat model with the specified configuration.
   *
   * @param config the OpenAI model configuration
   * @return a {@link Future} that completes with the initialized {@link OpenAiChatModel}
   */
  @Override
  public Future<OpenAiChatModel> initializeNonStreamingChatModel(OpenAIModelConfig config) {
    logger.info("Initializing OpenAI Chat Model...");

    return vertx.executeBlocking(() -> {
      chatModel = OpenAiChatModel.builder()
        .apiKey(config.getApiKey())
        .modelName(config.getModelName())
        .temperature(config.getTemperature())
        .topP(config.getTopP())
        .presencePenalty(config.getPresencePenalty())
        .frequencyPenalty(config.getFrequencyPenalty())
        .maxTokens(config.getMaxTokens())
        .responseFormat(config.getResponseFormat())
        .maxRetries(config.getMaxRetries())
        .stop(config.getStop())
        .logRequests(true)
        .logResponses(true)
        .build();
      logger.info("OpenAI Chat Model initialized successfully.");
      return chatModel;
    });
  }

  /**
   * Lazily initializes and returns an embedding model for OpenAI.
   *
   * @param apiKey             the OpenAI API key
   * @param embeddingModelName the embedding model name
   * @return the initialized {@link EmbeddingModel}
   */
  private synchronized EmbeddingModel initializeEmbeddingModel(String apiKey, String embeddingModelName) {
    logger.info("Initializing Embedding Model...");

    if (this.embeddingModel == null) {
      this.embeddingModel = OpenAiEmbeddingModel.builder()
        .modelName(embeddingModelName)
        .apiKey(apiKey)
        .logRequests(true)
        .logResponses(true)
        .build();
      logger.info("Embedding model initialized.");
    }
    return this.embeddingModel;
  }

  /**
   * Initializes a content retriever based on the embedding store and model.
   *
   * @param apiKey             the OpenAI API key
   * @param embeddingModelName the embedding model name
   * @param maxResult          maximum number of results to return
   * @param minScore           minimum similarity score threshold
   * @return a {@link Future} that completes with the initialized {@link ContentRetriever}
   */
  @Override
  public Future<ContentRetriever> initializeContentRetriever(String apiKey, String embeddingModelName, int maxResult, double minScore) {
    logger.info("Initializing Document Retriever...");

    return vertx.executeBlocking(() -> {
      contentRetriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(initializeEmbeddingModel(apiKey, embeddingModelName))
        .maxResults(maxResult)
        .minScore(minScore)
        .build();

      logger.info("Document retriever initialized successfully.");
      return contentRetriever;
    });
  }

  /**
   * Initializes an embedding store ingestor to process documents and store their embeddings.
   *
   * @param apiKey             the OpenAI API key
   * @param embeddingModelName the embedding model name
   * @return a {@link Future} that completes with the initialized {@link EmbeddingStoreIngestor}
   */
  @Override
  public Future<EmbeddingStoreIngestor> initializeEmbeddingStoreIngestor(String apiKey, String embeddingModelName) {
    logger.info("Initializing Embedding Store Ingestor");

    return vertx.executeBlocking(() -> {
      embeddingStoreIngestor = EmbeddingStoreIngestor.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(initializeEmbeddingModel(apiKey, embeddingModelName))
        .build();

      logger.info("Embedding Store Ingestor initialized successfully.");
      return embeddingStoreIngestor;
    });
  }


  /**
   * Initializes the retrieval augmentor that performs query transformation, retrieval, content injection, and aggregation.
   *
   * @param contentRetriever the content retriever for document lookup
   * @param chatModel        the OpenAI chat model for response generation
   * @return a {@link Future} that completes with the initialized {@link RetrievalAugmentor}
   */
  @Override
  public Future<RetrievalAugmentor> initializeRetrievalAugmentor(ContentRetriever contentRetriever, OpenAiChatModel chatModel) {
    logger.info("Initializing Retrieval Augmentor");

    return vertx.executeBlocking(() -> {
      retrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryTransformer(new CustomQueryTransformer(chatModel))
        .queryRouter(new DefaultQueryRouter(Collections.singleton(contentRetriever)))
        .contentAggregator(new DefaultContentAggregator())
        .contentInjector(new DefaultContentInjector())
        .build();

      logger.info("Retrieval Augmentor initialized successfully.");
      return retrievalAugmentor;
    });
  }
}
