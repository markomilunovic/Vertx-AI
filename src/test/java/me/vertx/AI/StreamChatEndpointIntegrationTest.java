package me.vertx.AI;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import vertx.AI.config.ConfigService;
import vertx.AI.verticle.MainVerticle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class StreamChatEndpointIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(StreamChatEndpointIntegrationTest.class);
  private static JsonObject config;
  private static final int PORT_NUMBER = 8088;

  @BeforeAll
  static void prepareConfig(Vertx vertx, VertxTestContext testContext) {
    ConfigService configService = new ConfigService(vertx);
    configService.getConfig().onSuccess(cfg -> {
      config = cfg;
      logger.info("Test config loaded.");
      testContext.completeNow();
    }).onFailure(err -> {
      logger.error("Failed to load config", err);
      testContext.failNow(err);
    });
  }

  @Test
  void shouldStreamResponseTokensWhenGivenSimpleMessage(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        logger.info("[Streaming-Simple] MainVerticle deployed.");

        WebClient client = WebClient.create(vertx);

        int port = config.getInteger("portNumber", PORT_NUMBER);
        String sessionId = UUID.randomUUID().toString();
        JsonObject body = new JsonObject()
          .put("message", "Hello")
          .put("sessionId", sessionId);

        client.post(port, "localhost", "/chat/stream")
          .putHeader("Accept", "text/event-stream")
          .as(BodyCodec.string())
          .sendJsonObject(body)
          .onSuccess(response -> {
            logger.info("[Streaming-Simple] Raw SSE: {}", response.body());

            assertEquals(200, response.statusCode());

            String rawStream = response.body();
            assertNotNull(rawStream);
            assertFalse(rawStream.trim().isEmpty());

            assertTrue(rawStream.contains("data:"));
            assertTrue(rawStream.toLowerCase().contains("hello") || rawStream.toLowerCase().contains("hi"));

            testContext.completeNow();
          })
          .onFailure(err -> {
            logger.error("Streaming call failed", err);
            testContext.failNow(err);
          });
      })
      .onFailure(testContext::failNow);
  }

  @Test
  void shouldStreamRelevantReplyAboutAirlinePolicy(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        logger.info("[Streaming-AirlinePolicy] MainVerticle deployed.");

        WebClient client = WebClient.create(vertx);

        int port = config.getInteger("portNumber", PORT_NUMBER);
        String sessionId = UUID.randomUUID().toString();
        JsonObject body = new JsonObject()
          .put("message", "What is the cancellation policy of SkyLux Airlines?")
          .put("sessionId", sessionId);

        client.post(port, "localhost", "/chat/stream")
          .putHeader("Accept", "text/event-stream")
          .as(BodyCodec.string())
          .sendJsonObject(body)
          .onSuccess(response -> {
            logger.info("[Streaming-AirlinePolicy] Raw SSE: {}", response.body());

            assertEquals(200, response.statusCode());

            String rawStream = response.body();
            assertNotNull(rawStream);
            assertFalse(rawStream.trim().isEmpty());
            assertTrue(rawStream.contains("data:"));

            String lowerStream = rawStream.toLowerCase();

            assertTrue(lowerStream.contains("refund") || lowerStream.contains("cancellation"),
              "Expected streamed response to mention cancellation or refund");

            testContext.completeNow();
          })
          .onFailure(err -> {
            logger.error("Streaming call failed", err);
            testContext.failNow(err);
          });
      })
      .onFailure(testContext::failNow);
  }

}
