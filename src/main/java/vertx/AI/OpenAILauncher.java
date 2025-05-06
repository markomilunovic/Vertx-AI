package vertx.AI;

import io.vertx.core.Vertx;
import vertx.AI.verticle.MainVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for launching the Vert.x-based OpenAI RAG application.
 * <p>
 * This class initializes the Vert.x runtime and deploys the {@link MainVerticle},
 * which is responsible for orchestrating the deployment of all other application components
 * including the HTTP server, document indexing, and OpenAI chat services.
 * </p>
 *
 * <p>
 * If the deployment is successful, a log message is printed. Otherwise, the error is logged
 * and the Vert.x instance is gracefully shut down.
 * </p>
 */
public class OpenAILauncher {

  private static final Logger logger = LoggerFactory.getLogger(OpenAILauncher.class);

  /**
   * Main method used to bootstrap the application.
   *
   * @param args command-line arguments (not used)
   */
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle(), res -> {
      if (res.succeeded()) {
        logger.info("MainVerticle deployed successfully!");
      } else {
        logger.error("Failed to deploy MainVerticle: ", res.cause());
        vertx.close();
      }
    });
  }
}
