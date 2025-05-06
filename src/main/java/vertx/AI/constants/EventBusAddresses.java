package vertx.AI.constants;

/**
 * A container for all EventBus address constants used throughout the application for
 * routing OpenAI chat requests and document indexing.
 */
public final class EventBusAddresses {
  public static final String OPENAI_CLIENT_NON_STREAMING = "openai.client.non_streaming";
  public static final String OPENAI_CLIENT_STREAMING = "openai.client.streaming";
  public static final String OPENAI_RESPONSE_STREAMING = "openai.response.streaming.";
  public static final String RAG_INDEX = "rag.index";

  /**
   * Private constructor to prevent instantiation.
   */
  private EventBusAddresses() {}
}

