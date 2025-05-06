package vertx.AI.config;

import io.vertx.core.json.JsonObject;

/**
 * Configuration class that encapsulates all model and retriever settings
 * required by the {@code OpenAIVerticle}, including streaming and non-streaming
 * model parameters, embedding model name, and retrieval thresholds.
 */
public class OpenAIVerticleConfig {

  private final OpenAIModelConfig streamingModelConfig;
  private final OpenAIModelConfig nonStreamingModelConfig;
  private final String embeddingModelName;
  private final int maxRetrieverResults;
  private final double minRetrieverScore;

  /**
   * Constructs the full OpenAI verticle configuration with the provided values.
   *
   * @param streamingModelConfig    Configuration for the streaming chat model
   * @param nonStreamingModelConfig Configuration for the non-streaming chat model
   * @param embeddingModelName      The name of the embedding model
   * @param maxRetrieverResults     Maximum number of documents retrieved for RAG
   * @param minRetrieverScore       Minimum similarity score for document inclusion
   */
  private OpenAIVerticleConfig(OpenAIModelConfig streamingModelConfig,
                               OpenAIModelConfig nonStreamingModelConfig,
                               String embeddingModelName,
                               int maxRetrieverResults,
                               double minRetrieverScore) {
    this.streamingModelConfig = streamingModelConfig;
    this.nonStreamingModelConfig = nonStreamingModelConfig;
    this.embeddingModelName = embeddingModelName;
    this.maxRetrieverResults = maxRetrieverResults;
    this.minRetrieverScore = minRetrieverScore;
  }

  /**
   * Builds an {@code OpenAIVerticleConfig} instance by parsing the provided
   * configuration object, usually loaded from {@code config.json}.
   *
   * @param config The JSON configuration containing OpenAI model settings
   * @return a fully initialized {@code OpenAIVerticleConfig}
   * @throws IllegalArgumentException if the API key is missing
   */
  public static OpenAIVerticleConfig from(JsonObject config) {
    String apiKey = config.getString("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalArgumentException("Missing OPENAI_API_KEY in configuration");
    }

    // -- Streaming model settings
    JsonObject streaming = config.getJsonObject("streamingChatModel", new JsonObject());
    OpenAIModelConfig streamingModel = OpenAIModelConfig.builder()
      .apiKey(apiKey)
      .modelName(streaming.getString("modelName", OpenAIConfigDefaults.MODEL_NAME))
      .temperature(streaming.getDouble("temperature", OpenAIConfigDefaults.TEMPERATURE))
      .topP(streaming.getDouble("topP", OpenAIConfigDefaults.TOP_P))
      .presencePenalty(streaming.getDouble("presencePenalty", OpenAIConfigDefaults.PRESENCE_PENALTY))
      .frequencyPenalty(streaming.getDouble("frequencyPenalty", OpenAIConfigDefaults.FREQUENCY_PENALTY))
      .maxTokens(streaming.getInteger("maxTokens", OpenAIConfigDefaults.MAX_TOKENS))
      .responseFormat(streaming.getString("responseFormat", OpenAIConfigDefaults.RESPONSE_FORMAT))
      .stop(ConfigUtil.extractStopList(streaming))
      .build();

    // -- Non-streaming model settings
    JsonObject nonStreaming = config.getJsonObject("chatModel", new JsonObject());
    OpenAIModelConfig nonStreamingModel = OpenAIModelConfig.builder()
      .apiKey(apiKey)
      .modelName(nonStreaming.getString("modelName", OpenAIConfigDefaults.MODEL_NAME))
      .temperature(nonStreaming.getDouble("temperature", OpenAIConfigDefaults.TEMPERATURE))
      .topP(nonStreaming.getDouble("topP", OpenAIConfigDefaults.TOP_P))
      .presencePenalty(nonStreaming.getDouble("presencePenalty", OpenAIConfigDefaults.PRESENCE_PENALTY))
      .frequencyPenalty(nonStreaming.getDouble("frequencyPenalty", OpenAIConfigDefaults.FREQUENCY_PENALTY))
      .maxTokens(nonStreaming.getInteger("maxTokens", OpenAIConfigDefaults.MAX_TOKENS))
      .responseFormat(nonStreaming.getString("responseFormat", OpenAIConfigDefaults.RESPONSE_FORMAT))
      .maxRetries(nonStreaming.getInteger("maxRetries", OpenAIConfigDefaults.MAX_RETRIES))
      .stop(ConfigUtil.extractStopList(nonStreaming))
      .build();

    // -- Embedding model name
    JsonObject embedding = config.getJsonObject("embeddingModel", new JsonObject());
    String embeddingModelName = embedding.getString("embeddingModel", OpenAIConfigDefaults.EMBEDDING_MODEL_NAME);

    // -- Content retriever settings
    JsonObject retriever = config.getJsonObject("contentRetriever", new JsonObject());
    int maxResults = retriever.getInteger("maxResult", OpenAIConfigDefaults.MAX_RESULT);
    double minScore = retriever.getDouble("minScore", OpenAIConfigDefaults.MIN_SCORE);

    return new OpenAIVerticleConfig(
      streamingModel,
      nonStreamingModel,
      embeddingModelName,
      maxResults,
      minScore
    );
  }

  /** @return the configuration for the streaming chat model */
  public OpenAIModelConfig getStreamingModelConfig() {
    return streamingModelConfig;
  }


  /** @return the configuration for the non-streaming chat model */
  public OpenAIModelConfig getNonStreamingModelConfig() {
    return nonStreamingModelConfig;
  }

  /** @return the name of the embedding model used for RAG */
  public String getEmbeddingModelName() {
    return embeddingModelName;
  }

  /** @return the maximum number of retriever results allowed */
  public int getMaxRetrieverResults() {
    return maxRetrieverResults;
  }

  /** @return the minimum similarity score required for retrieved documents */
  public double getMinRetrieverScore() {
    return minRetrieverScore;
  }
}
