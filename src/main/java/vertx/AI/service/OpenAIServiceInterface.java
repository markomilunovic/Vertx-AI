package vertx.AI.service;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.vertx.core.Future;
import vertx.AI.config.OpenAIModelConfig;

/**
 * Interface defining methods for initializing OpenAI-related services,
 * including chat models, embedding models, retrievers, and augmentors.
 */
public interface OpenAIServiceInterface {

  /**
   * Initializes a streaming OpenAI chat model with the given configuration.
   *
   * @param config the configuration for the streaming chat model
   * @return a Future containing the initialized {@link OpenAiStreamingChatModel}
   */
  Future<OpenAiStreamingChatModel> initializeStreamingChatModel(OpenAIModelConfig config);

  /**
   * Initializes a non-streaming OpenAI chat model with the given configuration.
   *
   * @param config the configuration for the chat model
   * @return a Future containing the initialized {@link OpenAiChatModel}
   */
  Future<OpenAiChatModel> initializeNonStreamingChatModel(OpenAIModelConfig config);

  /**
   * Initializes a content retriever using the provided API key and embedding configuration.
   *
   * @param apiKey the OpenAI API key
   * @param embeddingModelName the name of the embedding model
   * @param maxResult the maximum number of retrieved documents
   * @param minScore the minimum relevance score
   * @return a Future containing the initialized {@link ContentRetriever}
   */
  Future<ContentRetriever> initializeContentRetriever(String apiKey, String embeddingModelName, int maxResult, double minScore);

  /**
   * Initializes an embedding store ingestor for indexing documents.
   *
   * @param apiKey the OpenAI API key
   * @param embeddingModelName the name of the embedding model
   * @return a Future containing the initialized {@link EmbeddingStoreIngestor}
   */
  Future<EmbeddingStoreIngestor> initializeEmbeddingStoreIngestor(String apiKey, String embeddingModelName);

  /**
   * Initializes a retrieval augmentor using the provided retriever and chat model.
   *
   * @param contentRetriever the retriever for document content
   * @param chatModel the OpenAI chat model used in augmentation
   * @return a Future containing the initialized {@link RetrievalAugmentor}
   */
  Future<RetrievalAugmentor> initializeRetrievalAugmentor(ContentRetriever contentRetriever, OpenAiChatModel chatModel);
}
