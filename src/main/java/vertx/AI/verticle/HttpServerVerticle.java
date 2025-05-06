package vertx.AI.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import vertx.AI.config.OpenAIConfigDefaults;
import vertx.AI.dto.ErrorResponse;
import vertx.AI.constants.EventBusAddresses;
import vertx.AI.service.FileServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * {@code HttpServerVerticle} sets up and runs the HTTP server exposing endpoints for:
 * <ul>
 *   <li>Non-streaming chat requests at <b>/chat</b></li>
 *   <li>Streaming chat using Server-Sent Events at <b>/chat/stream</b></li>
 *   <li>File upload for document indexing at <b>/upload</b></li>
 * </ul>
 * <p>
 * This verticle handles routing logic and delegates file handling to {@link FileServiceInterface}.
 */
public class HttpServerVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(HttpServerVerticle.class);
  private String documentsDirectory;
  private final FileServiceInterface fileService;

  /**
   * Constructs the HTTP server verticle with the given file service.
   *
   * @param fileService service responsible for file handling and indexing
   */
  public HttpServerVerticle(FileServiceInterface fileService) {
    this.fileService = fileService;
  }

  /**
   * Starts the verticle by loading configuration and launching the HTTP server.
   *
   * @param startPromise promise indicating success or failure
   */
  @Override
  public void start(Promise<Void> startPromise) {
    logger.info("Starting HttpServerVerticle...");

    JsonObject config = config();

    documentsDirectory = config.getString("documentsDirectory", OpenAIConfigDefaults.DOCUMENTS_DIRECTORY);
    if (documentsDirectory.isEmpty()) {
      logger.warn("Documents directory is empty in config.json. Using default: documents/");
      documentsDirectory = "documents/";
    }

    startHttpServer(config, startPromise);
  }

  /**
   * Configures the routes and starts the HTTP server.
   *
   * @param config        application configuration
   * @param startPromise  startup promise to complete once the server starts
   */
  private void startHttpServer(JsonObject config, Promise<Void> startPromise) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setUploadsDirectory(documentsDirectory));

    router.post("/chat").handler(this::handleNonStreamingChatRequest);
    router.post("/chat/stream").handler(this::handleStreamingChatRequest);
    router.post("/upload").handler(this::handleFileUpload);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(config.getInteger("portNumber"), http -> {
        if (http.succeeded()) {
          logger.info("HTTP server running on port: {}", config.getInteger("portNumber"));
          startPromise.complete();
        } else {
          logger.error("Failed to start HTTP server: {}", http.cause().getMessage());
          startPromise.fail(http.cause());
        }
      });
  }

  /**
   * Handles non-streaming chat requests by forwarding them to the OpenAI event bus endpoint
   * and returning the full response as a JSON object.
   */
  private void handleNonStreamingChatRequest(RoutingContext context) {
    JsonObject request = context.body().asJsonObject();
    String userMessage = request.getString("message");

    if (userMessage == null || userMessage.trim().isEmpty()) {
      logger.warn("[Non-Streaming] Received empty message in HTTP request.");
      context.response()
        .setStatusCode(400)
        .end(ErrorResponse.createErrorResponse(400, "Message cannot be empty").encode());
      return;
    }

    String sessionId = request.getString("sessionId");
    if (sessionId == null || sessionId.isEmpty()) {
      sessionId = UUID.randomUUID().toString();
    }

    request.put("sessionId", sessionId);

    logger.info("Received non-streaming chat request for session: {}", sessionId);

    vertx.eventBus().request(EventBusAddresses.OPENAI_CLIENT_NON_STREAMING, request)
      .onSuccess(reply -> {
        JsonObject result = (JsonObject) reply.body();
        context.response()
          .putHeader("Content-Type", "application/json")
          .end(result.encode());
      })
      .onFailure(err -> {
        logger.error("Failed to process non-streaming chat request", err);
        context.response()
          .setStatusCode(500)
          .end(ErrorResponse.createErrorResponse(500, "Internal server error").encode());
      });
  }


  /**
   * Handles streaming chat requests using Server-Sent Events (SSE).
   * Sets up a message consumer on the event bus to stream tokens as they arrive.
   */
  private void handleStreamingChatRequest(RoutingContext context) {
    JsonObject request = context.body().asJsonObject();
    String userMessage = request.getString("message");

    if (userMessage == null || userMessage.trim().isEmpty()) {
      logger.warn("[Streaming] Received empty message in HTTP request.");
      context.response()
        .setStatusCode(400)
        .end(ErrorResponse.createErrorResponse(400, "Message cannot be empty").encode());
      return;
    }

    logger.info("Received chat request, initiating streaming...");

    // Get or generate a session ID
    String sessionId = request.getString("sessionId");
    if (sessionId == null || sessionId.isEmpty()) {
      sessionId = UUID.randomUUID().toString();
    }

    request.put("sessionId", sessionId);

    // Prepare an HTTP response as a Server-Sent Events (SSE) stream
    HttpServerResponse response = context.response();
    response.setChunked(true);
    response.putHeader("Content-Type", "text/event-stream");
    response.putHeader("Cache-Control", "no-cache");
    response.putHeader("Connection", "keep-alive");

    String finalSessionId = sessionId;
    MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(EventBusAddresses.OPENAI_RESPONSE_STREAMING + sessionId);

    consumer.handler(msg -> {
      JsonObject chunk = msg.body();

      // Send tokenized response chunks to the client
      if (chunk.containsKey("token")) {
        response.write("data: " + chunk.encode() + "\n\n");

        // End the connection when streaming is finished
      } else if (chunk.containsKey("end")) {
        logger.info("Streaming finished for session: {}", finalSessionId);

        if (!response.ended()) {
          response.end();
        }
        consumer.unregister();
      }
    });

    // Send request to OpenAIVerticle for processing
    vertx.eventBus().request(EventBusAddresses.OPENAI_CLIENT_STREAMING, request)
      .onFailure(err -> {
        logger.error("Failed to send request to OpenAIVerticle", err);
        if (!response.ended()) {
          response.setStatusCode(500).end(new JsonObject().put("error", "Internal server error").encode());
        }
        consumer.unregister();
      });

    // Handle client disconnection
    context.request().connection().closeHandler(v -> {
      logger.info("Client disconnected, cleaning up session {}", finalSessionId);
      consumer.unregister();
    });
  }

  /**
   * Handles file uploads by moving uploaded files to the documents directory
   * and triggering document indexing through the event bus.
   */
  private void handleFileUpload(RoutingContext context) {
    context.fileUploads().forEach(file -> {
      String destination = documentsDirectory + file.fileName();
      fileService.uploadFile(file.uploadedFileName(), destination, EventBusAddresses.RAG_INDEX)
        .onSuccess(v -> {
        logger.info("File uploaded: {}", file.fileName());
        context.response().setStatusCode(200).end("File uploaded: " + file.fileName());
      })
        .onFailure(err -> {
          logger.error("Failed to upload file: ", err);
          context.response()
            .setStatusCode(500)
            .end(ErrorResponse.createErrorResponse(500, "Failed to upload file").encode());
        });
    });
  }

}
