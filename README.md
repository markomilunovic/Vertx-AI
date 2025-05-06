# Vert.x AI Chatbot for BuraCloud

This project implements a modular, streaming-enabled OpenAI chatbot service using [Vert.x](https://vertx.io/) and [Langchain4j](https://github.com/langchain4j/langchain4j).

## Features

- Streaming & Non-Streaming OpenAI Chat via Langchain4j
- Retrieval-Augmented Generation (RAG) using in-memory embedding store
- Customizable Model Parameters (temperature, top_p, model, etc.)
- Dynamic File Uploads for RAG indexing

---

## Project Structure

```
src/
 └── main/
     └── java/
         └── vertx.AI/
             ├── config/                # Configuration loading and default model settings
             ├── constants/             # EventBus address constants
             ├── dto/                   # Request/response DTOs (e.g., errors)
             ├── rag/                   # RAG-specific helpers (e.g., custom query transformers)
             ├── service/               # Service interfaces and implementations
             │   ├── impl/              # Concrete service classes
             ├── util/                  # Utility classes (e.g., hashing)
             ├── verticle/              # Vert.x verticles (main logic, deployment)
             └── OpenAILauncher.java    # Standalone launcher for the application

```

### Core Verticles

- `OpenAIVerticle.java` - Handles OpenAI chat requests via Langchain4j
- `DocumentIndexVerticle.java` - Manages document uploads and embedding
- `HttpServerVerticle.java` (optional) - For local testing or debugging
- `MainVerticle.java` - Main entry point for Vert.x application (used in dev)
- `OpenAILauncher.java` - Launches the application in standalone mode

---

## How to Run

### Prerequisites

- Java 17 or 21
- Maven 3.8+
- OpenAI API Key in your environment or config file
- MongoDB Atlas cluster (with connection string, database name, and collection set up)



### Build the project

```bash
mvn clean package
```

### Run (local mode)

```bash
java -cp target/vertx-AI-1.0.0-SNAPSHOT.jar me.vertx.AI.OpenAILauncher
```

---

## MongoDB Atlas Setup

This project uses MongoDB Atlas to persist embedding vectors via Langchain4j's MongoDB integration.

You must provide the following in `config.json`:

```json
{
  "mongoConnectionString": "your-mongodb-connection-uri",
  "mongoDbName": "your-database-name",
  "mongoCollectionName": "your-collection-name",
  "mongoIndexName": "your-vector-index-name"
}

  ```

---

## Actions

| Action Name   | Description                             |
|---------------|-----------------------------------------|
| `chat`        | One-shot OpenAI completion (non-stream) |
| `chat_stream` | Streaming OpenAI completion             |
| `upload`      | Upload and embed document for RAG       |

---

## RAG (Retrieval-Augmented Generation)

This project uses:
- Langchain4j's `RetrievalAugmentor`
- In-memory `EmbeddingStore`
- Tika-based document parsing (`langchain4j-easy-rag`)

Upload `.txt`, `.pdf`, or `.docx` files via `index_document` action to enrich chatbot responses.

---

## Configuration

Configuration is handled via:
- `OpenAIModelConfig` – Defines model parameters such as temperature, top_p, max_tokens, etc.
- `OpenAIVerticleConfig` – Loads configuration values from `config.json`

All configurable values (e.g., API key, model name, embedding settings) should be defined in the `config.json` file located at the project root or passed programmatically.

Sensitive data like the OpenAI API key should never be hardcoded and should be securely stored in the config file or injected at runtime.


---

## Testing

Integration tests are located under `src/test/java` and use `VertxTestContext`.

To run tests:

```bash
mvn test
```

---

## License

MIT License – see `LICENSE` file.

---

## Author

**Marko Milunović**
[LinkedIn Profile](https://www.linkedin.com/in/marko-milunović-946428267)
