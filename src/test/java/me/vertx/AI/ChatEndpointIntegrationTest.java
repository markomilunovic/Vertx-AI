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
public class ChatEndpointIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ChatEndpointIntegrationTest.class);
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
  void shouldReturnResponseWhenGivenSimpleMessage(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        logger.info("[SimpleMessageTest] MainVerticle deployed.");

        WebClient client = WebClient.create(vertx);

        int port = config.getInteger("portNumber", PORT_NUMBER);
        String sessionId = UUID.randomUUID().toString();
        JsonObject body = new JsonObject()
          .put("message", "Hello")
          .put("sessionId", sessionId);

        client.post(port, "localhost", "/chat")
          .as(BodyCodec.jsonObject())
          .sendJsonObject(body)
          .onSuccess(response -> {
            logger.info("[SimpleMessageTest] Received response: {}", response.body());

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertTrue(response.body().containsKey("response"));

            String aiResponse = response.body().getString("response");
            assertNotNull(aiResponse);
            assertFalse(aiResponse.trim().isEmpty());

            testContext.completeNow();
          })
          .onFailure(err -> {
            logger.error("Failed to call /chat endpoint", err);
            testContext.failNow(err);
          });
      })
      .onFailure(err -> {
        logger.error("Failed to deploy MainVerticle for test", err);
        testContext.failNow(err);
      });
  }

  @Test
  void shouldUseContextToGenerateRelevantReplyAboutAirlinePolicy(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        logger.info("[AirlinePolicyTest] MainVerticle deployed.");

        WebClient client = WebClient.create(vertx);

        int port = config.getInteger("portNumber", PORT_NUMBER);
        String sessionId = UUID.randomUUID().toString();
        JsonObject body = new JsonObject()
          .put("message", "What is the cancellation policy of SkyLux Airlines?")
          .put("sessionId", sessionId);

        client.post(port, "localhost", "/chat")
          .as(BodyCodec.jsonObject())
          .sendJsonObject(body)
          .onSuccess(response -> {
            logger.info("[AirlinePolicyTest] Response: {}", response.body());

            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            assertTrue(response.body().containsKey("response"));

            String aiResponse = response.body().getString("response");
            assertNotNull(aiResponse);
            assertFalse(aiResponse.trim().isEmpty());
            assertTrue(aiResponse.toLowerCase().contains("refund") || aiResponse.toLowerCase().contains("cancellation"));

            testContext.completeNow();
          })
          .onFailure(err -> {
            logger.error("Failed to call /chat endpoint", err);
            testContext.failNow(err);
          });
      })
      .onFailure(err -> {
        logger.error("Failed to deploy MainVerticle for test", err);
        testContext.failNow(err);
      });
  }
}
