package dev.specra.api.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

  /**
   * The Vietnamese rows are the reason this class exists: without the decomposition step the
   * accented vowels are dropped rather than folded, and "Kiểm thử" slugs to "kim-th".
   */
  @ParameterizedTest
  @CsvSource({
    "Acme QA, acme-qa",
    "  Acme   QA  , acme-qa",
    "Kiểm thử thanh toán, kiem-thu-thanh-toan",
    "Đơn hàng, don-hang",
    "ĐƠN HÀNG, don-hang",
    "Acme -- QA!, acme-qa",
    "v2.0 release, v2-0-release",
  })
  void foldsAccentsAndCollapsesEverythingElseIntoHyphens(String input, String expected) {
    assertThat(Slugs.of(input, 64)).isEqualTo(expected);
  }

  @Test
  void returnsEmptyWhenNothingSurvives() {
    assertThat(Slugs.of("字", 64)).isEmpty();
    assertThat(Slugs.of("   ", 64)).isEmpty();
    assertThat(Slugs.of(null, 64)).isEmpty();
  }

  @Test
  void truncatesBackToAWordBoundary() {
    assertThat(Slugs.of("acme storefront checkout", 10)).isEqualTo("acme");
    assertThat(Slugs.of("acme storefront checkout", 15)).isEqualTo("acme-storefront");
    // No boundary to back off to: one word longer than the limit is cut where it falls.
    assertThat(Slugs.of("storefront", 5)).isEqualTo("store");
  }
}
