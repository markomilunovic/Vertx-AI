package vertx.AI.util;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.BsonDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for computing SHA-256 hashes of documents and managing their indexed state
 * in a MongoDB-backed embedding store.
 */
public class DocumentHashUtil {

  /**
   * Computes the SHA-256 hash of the contents of a file.
   *
   * @param filePath the path to the file
   * @return a hexadecimal string representing the SHA-256 hash
   * @throws IOException if the file cannot be read
   * @throws NoSuchAlgorithmException if SHA-256 is not supported on the system
   */
  public static String computeFileHash(Path filePath) throws IOException, NoSuchAlgorithmException {
    byte[] fileBytes = Files.readAllBytes(filePath);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hashBytes = digest.digest(fileBytes);
    return bytesToHex(hashBytes);
  }

  /**
   * Converts a byte array to its hexadecimal string representation.
   *
   * @param bytes the byte array to convert
   * @return a string of hexadecimal digits
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }


  /**
   * Checks whether a document with the given content hash already exists in the collection.
   *
   * @param collection the MongoDB collection to query
   * @param hash the content hash of the document
   * @return {@code true} if the document is already indexed; {@code false} otherwise
   */
  public static boolean isDocumentAlreadyIndexed(MongoCollection<BsonDocument> collection, String hash) {
    long count = collection.countDocuments(Filters.eq("metadata.content_hash", hash));
    return count > 0;
  }

  /**
   * Deletes all documents from the collection that match the given content hash.
   *
   * @param collection the MongoDB collection to modify
   * @param hash the content hash of the documents to delete
   */
  public static void deleteDocumentEmbeddings(MongoCollection<BsonDocument> collection, String hash) {
    collection.deleteMany(Filters.eq("metadata.content_hash", hash));
  }

}
