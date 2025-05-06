package vertx.AI.config;

import java.util.List;


/**
 * Configuration class that encapsulates various tunable parameters for an OpenAI model,
 * including temperature, token limits, stop sequences, and more.
 * <p>
 * This class is typically used with the {@link Builder} to create model-specific configurations
 * for both streaming and non-streaming OpenAI clients.
 */
public class OpenAIModelConfig {

  private String apiKey;
  private String modelName;
  private double temperature;
  private Double topP;
  private Double presencePenalty;
  private Double frequencyPenalty;
  private Integer maxTokens;
  private String responseFormat;
  private Integer maxRetries;
  private List<String> stop;

  /**
   * Private constructor to prevent instantiation.
   */
  private OpenAIModelConfig() {}

  /**
   * Creates a new instance of the {@link Builder} for fluent configuration.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder class for constructing {@link OpenAIModelConfig} instances.
   */
  public static class Builder {
    private final OpenAIModelConfig cfg = new OpenAIModelConfig();

    public Builder apiKey(String apiKey) {
      cfg.apiKey = apiKey;
      return this;
    }

    public Builder modelName(String modelName) {
      cfg.modelName = modelName;
      return this;
    }

    public Builder temperature(double t) {
      cfg.temperature = t;
      return this;
    }

    public Builder topP(Double topP) {
      cfg.topP = topP;
      return this;
    }

    public Builder presencePenalty(Double presencePenalty) {
      cfg.presencePenalty = presencePenalty;
      return this;
    }

    public Builder frequencyPenalty(Double frequencyPenalty) {
      cfg.frequencyPenalty = frequencyPenalty;
      return this;
    }

    public Builder maxTokens(Integer maxTokens) {
      cfg.maxTokens = maxTokens;
      return this;
    }

    public Builder responseFormat(String responseFormat) {
      cfg.responseFormat = responseFormat;
      return this;
    }

    public Builder maxRetries(int maxRetries) {
      cfg.maxRetries = maxRetries;
      return this;
    }

    public Builder stop(List<String> stop) {
      cfg.stop = stop;
      return this;
    }

    public OpenAIModelConfig build() {
      return cfg;
    }
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getModelName() {
    return modelName;
  }

  public double getTemperature() {
    return temperature;
  }

  public Double getTopP() {
    return topP;
  }

  public Double getPresencePenalty() {
    return presencePenalty;
  }

  public Double getFrequencyPenalty() {
    return frequencyPenalty;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public String getResponseFormat() {
    return responseFormat;
  }

  public Integer getMaxRetries() { return maxRetries; }

  public List<String> getStop() {
    return stop;
  }

}

