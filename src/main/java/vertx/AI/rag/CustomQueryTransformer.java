package vertx.AI.rag;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A custom implementation of {@link QueryTransformer} that first compresses the query using
 * {@link CompressingQueryTransformer}, then expands each compressed query using
 * {@link ExpandingQueryTransformer}. This enhances retrieval relevance by both generalizing
 * and specializing the user's input.
 */
public class CustomQueryTransformer implements QueryTransformer {

  private final CompressingQueryTransformer compressingQueryTransformer;
  private final ExpandingQueryTransformer expandingQueryTransformer;

  /**
   * Constructs a new {@code CustomQueryTransformer} using the provided OpenAI chat model
   * for both compression and expansion phases.
   *
   * @param chatModel the {@link OpenAiChatModel} used internally by transformers
   */
  public CustomQueryTransformer(OpenAiChatModel chatModel) {
    this.compressingQueryTransformer = new CompressingQueryTransformer(chatModel);
    this.expandingQueryTransformer = new ExpandingQueryTransformer(chatModel);
  }

  /**
   * Transforms the original query by compressing it into concise forms, then expanding
   * each compressed query into one or more detailed queries.
   *
   * @param query the original user query
   * @return a collection of transformed queries ready for retrieval
   */
  @Override
  public Collection<Query> transform(Query query) {
    Collection<Query> compressedQueries = compressingQueryTransformer.transform(query);
    List<Query> finalExpandedQueries = new ArrayList<>();

    for (Query compressedQuery : compressedQueries) {
      finalExpandedQueries.addAll(expandingQueryTransformer.transform(compressedQuery));
    }
    return finalExpandedQueries;
  }
}
