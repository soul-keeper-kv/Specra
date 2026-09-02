package dev.specra.api.feature.testmodel.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specra.api.core.testmodel.TestModel;
import dev.specra.api.core.testmodel.TestModelJson;
import dev.specra.api.feature.testcase.dto.TestCaseStepResponse;
import dev.specra.api.feature.testmodel.dto.PageReference;
import dev.specra.api.feature.testmodel.dto.SourceCoverage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TestModelViewsTest {

  private static TestModel login() throws IOException {
    return TestModelJson.read(
        new String(
            new ClassPathResource("testmodel/fixtures/valid/login.json")
                .getInputStream()
                .readAllBytes(),
            StandardCharsets.UTF_8));
  }

  @Test
  void pagesAreTheDistinctTargetsWithTheirElementsAndSteps() throws IOException {
    List<PageReference> pages = TestModelViews.pages(login());

    assertThat(pages).extracting(PageReference::name).containsExactly("LoginPage", "DashboardPage");
    assertThat(pages.get(0).elements())
        .containsExactly("usernameInput", "passwordInput", "submitButton");
    assertThat(pages.get(0).stepIds()).containsExactly("s1", "s2", "s3", "s4");
    assertThat(pages.get(1).elements()).containsExactly("heading");
    assertThat(pages.get(1).stepIds()).containsExactly("s5");
  }

  /** A manual step the model never mentioned is the gap the reviewer must see. */
  @Test
  void coverageListsEveryManualStepEvenTheOnesTheModelDropped() throws IOException {
    List<TestCaseStepResponse> manual =
        List.of(
            new TestCaseStepResponse(1, "Open the login page", null, null),
            new TestCaseStepResponse(2, "Enter the username", "qa", null),
            new TestCaseStepResponse(3, "Enter the password", null, null),
            new TestCaseStepResponse(4, "Submit", null, null),
            new TestCaseStepResponse(5, "Check the dashboard", null, "Dashboard shows"),
            new TestCaseStepResponse(6, "Log out again", null, null));

    List<SourceCoverage> coverage = TestModelViews.coverage(login(), manual);

    assertThat(coverage).hasSize(6);
    assertThat(coverage.get(0).sourceStepId()).isEqualTo("ts-1");
    assertThat(coverage.get(4).modelStepIds()).containsExactly("s5");
    assertThat(coverage.get(5).sourceStepId()).isEqualTo("ts-6");
    assertThat(coverage.get(5).modelStepIds()).isEmpty();
  }

  @Test
  void checksumIsPlainSha256Hex() {
    assertThat(TestModelViews.sha256(""))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
