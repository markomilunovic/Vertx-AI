package vertx.AI.dto;

import io.vertx.core.json.JsonObject;


/**
 * A simple DTO representing a structured error response with an HTTP status and error message.
 */
public record ErrorResponse(int status, String error) {

  /**
   * Converts this error response into a {@link JsonObject} for HTTP or EventBus transmission.
   *
   * @return a {@code JsonObject} containing the status and error message
   */
  public JsonObject toJson() {
    return new JsonObject()
      .put("status", status)
      .put("error", error);
  }


  /**
   * Creates a new {@code JsonObject} error response directly from status and message values.
   *
   * @param status  the HTTP status code to return
   * @param message the error message to include
   * @return a {@code JsonObject} with the error details
   */
  public static JsonObject createErrorResponse(int status, String message) {
    return new ErrorResponse(status, message).toJson();
  }

}
