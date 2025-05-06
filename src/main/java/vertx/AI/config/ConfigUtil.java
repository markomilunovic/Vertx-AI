package vertx.AI.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for working with configuration JSON objects.
 * <p>
 * Provides helper methods to extract specific structured values
 * from configuration sections.
 */
public class ConfigUtil {

  /**
   * Private constructor to prevent instantiation.
   */
  private ConfigUtil() {}

  /**
   * Extracts a list of strings from a "stop" field in a given JSON config section.
   * This is commonly used in OpenAI model configurations to define stop sequences.
   *
   * @param configSection the JSON object containing the stop array
   * @return a list of stop sequences if present, otherwise {@code null}
   */
  public static List<String> extractStopList(JsonObject configSection) {
    if (configSection == null) {
      return null;
    }

    JsonArray stopArray = configSection.getJsonArray("stop");
    if (stopArray == null) {
      return null;
    }

    return stopArray.stream()
      .filter(item -> item instanceof String)
      .map(Object::toString)
      .collect(Collectors.toList());
  }
}
