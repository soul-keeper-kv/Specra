package dev.specra.api.feature.run.service;

import dev.specra.api.feature.run.domain.AutomationTest;
import dev.specra.api.feature.run.domain.AutomationTestRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where a test case's generated code currently lives in the repository.
 *
 * <p>One row per test case, rewritten every time a generation is applied — the *current* state, not
 * the history, which is what `code_generations` is for. A run reads this to answer "which spec file
 * do I execute for this case", which no proposal row can answer once several have been applied and
 * superseded.
 *
 * <p>It lives in the run feature rather than in codegen because a run is its only reader, and a
 * feature owning the rows it reads is what keeps the arrow pointing one way. Codegen calls {@link
 * #record} on apply, which is a feature using another feature — allowed, never circular.
 */
@Service
@Transactional(readOnly = true)
public class AutomationTestService {

  private final AutomationTestRepository repository;

  public AutomationTestService(AutomationTestRepository repository) {
    this.repository = repository;
  }

  /**
   * Records that this test case's code is now at {@code specPath}, committed at {@code commitSha}.
   *
   * <p>Upserted rather than appended: a case has one current projection, and a second row would
   * make "which spec runs this case" ambiguous the first time somebody regenerated.
   */
  @Transactional
  public void record(
      UUID workspaceId,
      UUID testCaseId,
      String specPath,
      String testTitle,
      UUID testModelId,
      String commitSha) {
    AutomationTest automationTest =
        repository
            .findByTestCaseId(testCaseId)
            .orElseGet(
                () -> {
                  AutomationTest created = new AutomationTest();
                  created.setWorkspaceId(workspaceId);
                  created.setTestCaseId(testCaseId);
                  return created;
                });

    automationTest.setSpecPath(specPath);
    automationTest.setTestTitle(testTitle);
    automationTest.setCurrentModelId(testModelId);
    automationTest.setLastCommitSha(commitSha);
    repository.save(automationTest);
  }
}
