package dev.specra.api.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The caller here is usually a language model, so these are not defensive-programming niceties:
 * they are the reason a hallucinated {@code limit} of 10000 cannot turn one chat message into a
 * full table scan.
 */
class ContentQueryTest {

  @Test
  void clampsPageAndSizeIntoARangeAStoreCanServe() {
    assertThat(new ContentQuery("x", null, -5, 10_000).page()).isZero();
    assertThat(new ContentQuery("x", null, -5, 10_000).size()).isEqualTo(ContentQuery.MAX_SIZE);
    assertThat(new ContentQuery("x", null, 0, 0).size()).isEqualTo(ContentQuery.DEFAULT_SIZE);
    assertThat(new ContentQuery("x", null, 2, 5).page()).isEqualTo(2);
  }

  @Test
  void blankFiltersBecomeNoFilterRatherThanAFilterOnNothing() {
    ContentQuery query = new ContentQuery("   ", "  ", 0, 10);

    assertThat(query.text()).isNull();
    assertThat(query.tag()).isNull();
  }

  @Test
  void tagsAreLowerCasedBecauseThatIsHowStoresKeepThem() {
    assertThat(new ContentQuery(null, " Q3 ", 0, 10).tag()).isEqualTo("q3");
  }

  @Test
  void excerptCollapsesWhitespaceAndTruncates() {
    String body = "a\n\n  b" + " c".repeat(500);

    String excerpt = ContentSummary.excerpt(body);

    assertThat(excerpt).startsWith("a b c").endsWith("…");
    assertThat(excerpt).hasSize(ContentSummary.EXCERPT_CHARS + 1);
  }
}
