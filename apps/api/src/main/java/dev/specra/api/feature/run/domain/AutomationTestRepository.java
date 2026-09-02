package dev.specra.api.feature.run.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationTestRepository extends JpaRepository<AutomationTest, UUID> {

  Optional<AutomationTest> findByTestCaseId(UUID testCaseId);

  List<AutomationTest> findByTestCaseIdIn(Collection<UUID> testCaseIds);

  /**
   * Every automation test of a project, reached through its test cases.
   *
   * <p>The join is here rather than a {@code project_id} column because the row belongs to the test
   * case: a case that moved would otherwise leave this pointing at the wrong project.
   */
  @Query(
      """
      select a from AutomationTest a
      where a.testCaseId in (select tc.id from TestCase tc where tc.projectId = :projectId)
      """)
  List<AutomationTest> findByProjectId(@Param("projectId") UUID projectId);
}
