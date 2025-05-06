package vertx.AI.service.impl;

import com.mongodb.client.MongoCollection;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import vertx.AI.util.DocumentHashUtil;
import vertx.AI.service.FileServiceInterface;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for handling file-related operations including uploads and indexing for RAG (Retrieval-Augmented Generation).
 * Interacts with the file system and MongoDB to manage document ingestion into the embedding store.
 */
public class FileService implements FileServiceInterface {
  private static final Logger logger = LoggerFactory.getLogger(FileService.class);
  private final Vertx vertx;
  private final FileSystem fs;
  private final MongoCollection<BsonDocument> collection;

  /**
   * Constructs a new FileService.
   *
   * @param vertx      the Vert.x instance used for file system and async operations
   * @param collection the MongoDB collection used for tracking indexed documents
   */
  public FileService(Vertx vertx, MongoCollection<BsonDocument> collection) {
    this.vertx = vertx;
    this.fs = vertx.fileSystem();
    this.collection = collection;
  }

  /**
   * Uploads a file by moving it to the target directory and triggering a re-indexing event on the event bus.
   *
   * @param uploadedFileName the path of the uploaded temp file
   * @param destination      the destination path to move the file to
   * @param indexAddress     the event bus address to trigger indexing
   * @return a {@link Future} indicating the success or failure of the operation
   */
  @Override
  public Future<Void> uploadFile(String uploadedFileName, String destination, String indexAddress) {
    return fs.move(uploadedFileName, destination)
      .onSuccess(v -> {
        logger.info("File moved to: {}", destination);
        vertx.eventBus().send(indexAddress, destination);
      })
      .onFailure(err -> logger.error("Failed to move file", err));
  }

  /**
   * Indexes all documents in the given directory that have not already been indexed.
   *
   * @param directoryPath the path to the documents directory
   * @param ingestor      the embedding store ingestor to ingest new documents
   * @return a {@link Future} representing the result of the indexing operation
   */
  @Override
  public Future<Void> indexDocuments(String directoryPath, EmbeddingStoreIngestor ingestor) {
    return vertx.executeBlocking(() -> {
      try (Stream<Path> fileStream = Files.list(Paths.get(directoryPath))) {
        fileStream
          .filter(filePath -> !filePath.getFileName().toString().startsWith("."))
          .forEach(filePath -> {
            try {
              String hash = DocumentHashUtil.computeFileHash(filePath);
              boolean alreadyIndexed = DocumentHashUtil.isDocumentAlreadyIndexed(collection, hash);

              if (alreadyIndexed) {
                logger.info("Skipping indexing. Document already indexed: {}", filePath);
              } else {
                Document document = FileSystemDocumentLoader.loadDocument(filePath);
                document.metadata().put("content_hash", hash);
                ingestor.ingest(List.of(document));
                logger.info("Indexed new document: {}", filePath);
              }
            } catch (Exception e) {
              logger.error("Error processing document: {} - Skipping file.", filePath, e);
            }
          });
      } catch (Exception e) {
        logger.error("Error listing files in directory: {}", directoryPath, e);
        throw new RuntimeException(e);
      }
      return null;
    });
  }

  /**
   * Indexes a single file if it has not been previously indexed based on its content hash.
   *
   * @param filePath the path to the file to check and index
   * @param ingestor the embedding store ingestor to use for ingestion
   * @return a {@link Future} indicating completion of the indexing task
   */
  @Override
  public Future<Void> indexFileIfNotIndexed(String filePath, EmbeddingStoreIngestor ingestor) {
    return vertx.executeBlocking(() -> {
      try {
        Path path = Paths.get(filePath);
        if (Files.isDirectory(path) || path.getFileName().toString().startsWith(".")) {
          logger.info("Skipping directory or hidden file: {}", path);
          return null;
        }

        String hash = DocumentHashUtil.computeFileHash(path);
        boolean alreadyIndexed = DocumentHashUtil.isDocumentAlreadyIndexed(collection, hash);

        if (alreadyIndexed) {
          logger.info("Skipping reindexing. Document already indexed: {}", filePath);
        } else {
          Document document = FileSystemDocumentLoader.loadDocument(path);
          document.metadata().put("content_hash", hash);
          ingestor.ingest(List.of(document));
          logger.info("Re-indexed new document: {}", filePath);
        }
      } catch (Exception e) {
        logger.error("Error re-indexing document: {} - Skipping.", filePath, e);
      }
      return null;
    });
  }
}
