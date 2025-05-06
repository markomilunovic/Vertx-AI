package me.vertx.AI;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import vertx.AI.config.ConfigService;
import vertx.AI.verticle.MainVerticle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class UploadDocumentEndpointIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(UploadDocumentEndpointIntegrationTest.class);
  private static JsonObject config;
  private static final int PORT_NUMBER = 8088;
  private static final String TEST_FILE_PATH = "/mnt/data/test-documents/TestUploadDocument.txt";

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
  void shouldUploadDocumentSuccessfully(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), new DeploymentOptions().setConfig(config))
      .onSuccess(id -> {
        logger.info("MainVerticle deployed for document upload test.");

        WebClient client = WebClient.create(vertx);
        FileSystem fs = vertx.fileSystem();
        Path path = Paths.get(TEST_FILE_PATH);

        if (!fs.existsBlocking(path.toString())) {
          testContext.failNow(new RuntimeException("Test file does not exist: " + path));
          return;
        }

        MultipartForm form = MultipartForm.create()
          .binaryFileUpload("file", path.getFileName().toString(), path.toString(), "text/plain");

        HttpRequest<Buffer> request = client.request(HttpMethod.POST, config.getInteger("portNumber", PORT_NUMBER), "localhost", "/upload");

        request.sendMultipartForm(form)
          .onSuccess(response -> {
            logger.info("Upload response status: {}", response.statusCode());
            String responseText = response.bodyAsString();
            logger.info("Upload response: {}", responseText);

            assertEquals(200, response.statusCode());
            assertTrue(responseText.contains("File uploaded"));

            testContext.completeNow();
          })
          .onFailure(err -> {
            logger.error("Upload failed", err);
            testContext.failNow(err);
          });
      })
      .onFailure(err -> {
        logger.error("MainVerticle deployment failed", err);
        testContext.failNow(err);
      });
  }
}
