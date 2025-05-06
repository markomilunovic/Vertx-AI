package vertx.AI.config;

/**
 * Provides default configuration values for OpenAI models and embedding settings.
 * <p>
 * These constants serve as fallback values if specific parameters are not defined
 * in the runtime configuration (e.g., in {@code config.json} or environment variables).
 */
public class OpenAIConfigDefaults {

  /**
   * Private constructor to prevent instantiation.
   */
  private OpenAIConfigDefaults() {}

  public static final String MODEL_NAME = "gpt-3.5-turbo";
  public static final double TEMPERATURE = 0.2;
  public static final double TOP_P = 1.0;
  public static final double PRESENCE_PENALTY = 0.0;
  public static final double FREQUENCY_PENALTY = 0.0;
  public static final int MAX_TOKENS = 4000;
  public static final String RESPONSE_FORMAT = "text";
  public static final int MAX_RETRIES = 3;

  public static final String EMBEDDING_MODEL_NAME = "text-embedding-3-small";
  public static final int MAX_RESULT = 5;
  public static final double MIN_SCORE = 0.7;

  public static final String DOCUMENTS_DIRECTORY = "documents/";
}
