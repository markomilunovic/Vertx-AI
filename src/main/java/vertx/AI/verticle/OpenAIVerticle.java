package vertx.AI.verticle;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import vertx.AI.config.OpenAIConfigDefaults;
import vertx.AI.config.OpenAIVerticleConfig;
import vertx.AI.dto.ErrorResponse;
import vertx.AI.constants.EventBusAddresses;
import vertx.AI.service.OpenAIServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verticle responsible for handling chat interactions using OpenAI's API.
 * It supports both non-streaming and streaming responses with retrieval-augmented generation (RAG).
 * <p>
 * This verticle:
 * <ul>
 *     <li>Initializes the OpenAI chat and streaming models</li>
 *     <li>Handles requests for non-streaming replies via the event bus</li>
 *     <li>Handles streaming responses using token-by-token delivery via SSE</li>
 * </ul>
 */
public class OpenAIVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(OpenAIVerticle.class);

  private OpenAiStreamingChatModel streamingChatModel;
  private OpenAiChatModel chatModel;
  private final Map<String, ChatMemory> chatMemories = new HashMap<>();
  private ContentRetriever contentRetriever;
  private RetrievalAugmentor retrievalAugmentor;
  private final OpenAIServiceInterface openAIService;

  /**
   * Constructor accepting a service interface for OpenAI operations.
   *
   * @param openAIService the service used to initialize and interact with OpenAI components
   */
  public OpenAIVerticle(OpenAIServiceInterface openAIService) {
    this.openAIService = openAIService;
  }

  /**
   * Initializes chat models, retrievers, and augmentors.
   * Registers event bus consumers for non-streaming and streaming chat.
   *
   * @param startPromise startup completion handler
   */
  @Override
  public void start(Promise<Void> startPromise) {
    OpenAIVerticleConfig cfg;
    try {
      cfg = OpenAIVerticleConfig.from(config());
    } catch (IllegalArgumentException e) {
      startPromise.fail(e);
      return;
    }

    openAIService.initializeStreamingChatModel(cfg.getStreamingModelConfig())
      .compose(streamingModel -> {
        this.streamingChatModel = streamingModel;
        return openAIService.initializeContentRetriever(
          cfg.getStreamingModelConfig().getApiKey(),
          cfg.getEmbeddingModelName(),
          cfg.getMaxRetrieverResults(),
          cfg.getMinRetrieverScore()
        );
      })
      .compose(retriever -> {
        this.contentRetriever = retriever;
        return openAIService.initializeNonStreamingChatModel(cfg.getNonStreamingModelConfig());
      })
      .compose(model -> {
        this.chatModel = model;
        return openAIService.initializeRetrievalAugmentor(contentRetriever, chatModel);
      })
      .onSuccess(augmentor -> {
        this.retrievalAugmentor = augmentor;
        vertx.eventBus().consumer(EventBusAddresses.OPENAI_CLIENT_NON_STREAMING, this::handleNonStreamingChatRequest);
        vertx.eventBus().consumer(EventBusAddresses.OPENAI_CLIENT_STREAMING, this::handleStreamingChatRequest);
        logger.info("OpenAI services initialized successfully.");
        startPromise.complete();
      })
      .onFailure(err -> {
        logger.error("Failed to initialize OpenAI services.", err);
        startPromise.fail(err);
      });
  }

  /**
   * Handles non-streaming chat requests from the event bus.
   * Performs context retrieval using RAG and returns a complete AI response.
   *
   * @param message the incoming event bus message containing user message and session ID
   */
  private void handleNonStreamingChatRequest(Message<JsonObject> message) {
    String userMessage = message.body().getString("message");
    String sessionId = message.body().getString("sessionId");

    if (userMessage == null || userMessage.isEmpty()) {
      logger.warn("[Non-Streaming] Empty message received, rejecting request.");
      message.fail(400, ErrorResponse.createErrorResponse(400, "Message cannot be empty").encode());
      return;
    }

    logger.info("Handling non-streaming chat for session: {}", sessionId);

    UserMessage originalMessage = new UserMessage(userMessage);

    List<ChatMessage> chatHistory = chatMemories.containsKey(sessionId)
      ? chatMemories.get(sessionId).messages()
      : Collections.emptyList();

    Metadata metadata = Metadata.from(originalMessage, sessionId, chatHistory);
    AugmentationRequest augmentationRequest = new AugmentationRequest(originalMessage, metadata);

    vertx.executeBlocking(() -> retrievalAugmentor.augment(augmentationRequest))
      .onSuccess(augmentationResult -> {
        String retrievedText;
        try {
          UserMessage augmentedMessage = (UserMessage) augmentationResult.chatMessage();
          retrievedText = augmentedMessage.singleText();
        } catch (ClassCastException e) {
          logger.warn("Augmentation result is not a UserMessage, using fallback.");
          retrievedText = augmentationResult.chatMessage().toString();
        }

        if (retrievedText == null || retrievedText.trim().isEmpty()) {
          retrievedText = "No relevant documents found.";
        }

        logger.info("[Non-Streaming][Session: {}] Retrieved context:\n{}", sessionId, retrievedText);

        String augmentedUserMessage = "Context:\n" + retrievedText + "\n\nUser Question:\n" + userMessage;

        Tokenizer tokenizer = new OpenAiTokenizer(OpenAiChatModelName.GPT_3_5_TURBO);

        int maxTokens = config().getInteger("maxTokens", OpenAIConfigDefaults.MAX_TOKENS);
        ChatMemory chatMemory = chatMemories.computeIfAbsent(sessionId,
          id -> TokenWindowChatMemory.withMaxTokens(maxTokens, tokenizer));

        chatMemory.add(new UserMessage(augmentedUserMessage));

        logger.info("Non-Streaming OpenAI response for: {}", augmentedUserMessage);

        Response<AiMessage> response = chatModel.generate(chatMemory.messages());
        chatMemory.add(response.content());

        JsonObject result = new JsonObject().put("response", response.content().text());
        message.reply(result);
      })
      .onFailure(err -> {
        logger.error("Failed to process non-streaming chat request", err);
        message.fail(500, ErrorResponse.createErrorResponse(500, "Failed to process request").encode());
      });
  }

  /**
   * Handles streaming chat requests from the event bus.
   * Uses RAG to retrieve context, augments the prompt, and streams the response token-by-token.
   *
   * @param message the incoming event bus message containing user message and session ID
   */
  private void handleStreamingChatRequest(Message<JsonObject> message) {
    String userMessage = message.body().getString("message");
    String sessionId = message.body().getString("sessionId");

    if (userMessage.isEmpty()) {
      logger.warn("[Streaming] Empty message received, rejecting request.");
      message.fail(400, ErrorResponse.createErrorResponse(400, "Message cannot be empty").encode());
      return;
    }

    logger.info("Handling chat for session: {}", sessionId);

    UserMessage originalMessage = new UserMessage(userMessage);

    List<ChatMessage> chatHistory = chatMemories.containsKey(sessionId)
      ? chatMemories.get(sessionId).messages()
      : Collections.emptyList();

    Metadata metadata = Metadata.from(originalMessage, sessionId, chatHistory);

    AugmentationRequest augmentationRequest = new AugmentationRequest(originalMessage, metadata);

    vertx.executeBlocking(() -> retrievalAugmentor.augment(augmentationRequest))
      .onSuccess(augmentationResult -> {

        String retrievedText;
        try {
          UserMessage augmentedMessage = (UserMessage) augmentationResult.chatMessage();
          retrievedText = augmentedMessage.singleText();
        } catch (ClassCastException e) {
          logger.warn("Augmentation result is not a UserMessage, using fallback conversion.");
          retrievedText = augmentationResult.chatMessage().toString();
        }

        if (retrievedText == null || retrievedText.trim().isEmpty()) {
          retrievedText = "No relevant documents found.";
        }

        logger.info("[Streaming][Session: {}] Retrieved context:\n{}", sessionId, retrievedText);

        String augmentedUserMessage = "Context:\n" + retrievedText + "\n\nUser Question:\n" + userMessage;

        Tokenizer tokenizer = new OpenAiTokenizer(OpenAiChatModelName.GPT_3_5_TURBO);

        // Use TokenWindowChatMemory with a token-based limit
        int maxTokens = config().getInteger("maxTokens", OpenAIConfigDefaults.MAX_TOKENS);
        ChatMemory chatMemory = chatMemories.computeIfAbsent(sessionId, id -> TokenWindowChatMemory.withMaxTokens(maxTokens, tokenizer));
        chatMemory.add(new UserMessage(augmentedUserMessage));

        // Stream OpenAI response token-by-token
        logger.info("Streaming OpenAI response for: {}", augmentedUserMessage);

        streamingChatModel.generate(chatMemory.messages(), new StreamingResponseHandler<>() {
          @Override
          public void onNext(String token) {
            if (token != null && !token.isEmpty()) {
              logger.info("Sending token: {}", token);
              JsonObject chunkMessage = new JsonObject().put("token", token);
              vertx.eventBus().publish(EventBusAddresses.OPENAI_RESPONSE_STREAMING + sessionId, chunkMessage);
            }
          }

          @Override
          public void onComplete(Response<AiMessage> response) {
            String aiResponse = response.content().text();
            chatMemory.add(new AiMessage(aiResponse));

            logger.info("OpenAI streaming completed.");

            JsonObject endMessage = new JsonObject().put("end", true);
            vertx.eventBus().publish(EventBusAddresses.OPENAI_RESPONSE_STREAMING + sessionId, endMessage);
          }

          @Override
          public void onError(Throwable error) {
            logger.error("OpenAI streaming error: ", error);
            message.fail(500, ErrorResponse.createErrorResponse(500, "OpenAI API streaming error: " + error.getMessage()).encode());
          }
        });

        message.reply(new JsonObject().put("status", "streaming_started"));
      })
      .onFailure(err -> {
        logger.error("Failed to process augmentation request", err);
        message.fail(500, ErrorResponse.createErrorResponse(500, "Failed to process augmentation request").encode());
      });
  }
}

