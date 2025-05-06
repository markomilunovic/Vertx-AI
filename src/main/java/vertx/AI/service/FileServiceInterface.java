package vertx.AI.service;

import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.vertx.core.Future;


/**
 * Interface defining file operations related to document indexing and uploading.
 */
public interface FileServiceInterface {

  /**
   * Uploads a file by moving it to the specified destination and sending an index event.
   *
   * @param uploadedFileName the temporary uploaded file path
   * @param destination the destination path to move the file
   * @param indexAddress the event bus address to notify for indexing
   * @return a Future indicating completion of the file move operation
   */
  Future<Void> uploadFile(String uploadedFileName, String destination, String indexAddress);

  /**
   * Indexes all documents in the given directory using the provided ingestor.
   *
   * @param directoryPath the path of the directory to scan for documents
   * @param embeddingStoreIngestor the ingestor used to index the documents
   * @return a Future indicating completion of the indexing process
   */
  Future<Void> indexDocuments(String directoryPath, EmbeddingStoreIngestor embeddingStoreIngestor);

  /**
   * Indexes a single file if it has not already been indexed.
   *
   * @param filePath the path to the file
   * @param embeddingStoreIngestor the ingestor used for indexing
   * @return a Future indicating completion of the indexing operation
   */
  Future<Void> indexFileIfNotIndexed(String filePath, EmbeddingStoreIngestor embeddingStoreIngestor);
}
