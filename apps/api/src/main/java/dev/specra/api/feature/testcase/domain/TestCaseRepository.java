package dev.specra.api.feature.testcase.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

  Optional<TestCase> findByProjectIdAndExternalSourceAndExternalId(
      UUID projectId, String externalSource, String externalId);

  /**
   * The cast on {@code :q} is load-bearing, exactly as in the other repositories: {@code concat}
   * gives PostgreSQL nothing to infer an untyped parameter from, and the unfiltered listing — the
   * most common request — would die on {@code lower(bytea)}.
   */
  @Query(
      """
      select distinct tc from TestCase tc
      left join tc.tags t
      where (:projectId is null or tc.projectId = :projectId)
        and (:q is null or lower(tc.title) like lower(concat('%', cast(:q as string), '%'))
                        or lower(tc.reference) like lower(concat('%', cast(:q as string), '%')))
        and (:status is null or tc.automationStatus = :status)
        and (:tag is null or t = :tag)
      """)
  Page<TestCase> search(
      @Param("projectId") UUID projectId,
      @Param("q") String q,
      @Param("status") AutomationStatus status,
      @Param("tag") String tag,
      Pageable pageable);

  /**
   * The same search, narrowed to a set of workspaces rather than to one project.
   *
   * <p>What the assistant retrieves through. A cross-project query on a multi-tenant table has to
   * name the tenants it may see, and the caller passes a non-empty set — an empty one means the
   * user is in no workspace at all, which the service answers without a query.
   */
  @Query(
      """
      select distinct tc from TestCase tc
      left join tc.tags t
      where tc.workspaceId in :workspaceIds
        and (:q is null or lower(tc.title) like lower(concat('%', cast(:q as string), '%'))
                        or lower(tc.reference) like lower(concat('%', cast(:q as string), '%')))
        and (:tag is null or t = :tag)
      """)
  Page<TestCase> searchInWorkspaces(
      @Param("workspaceIds") Collection<UUID> workspaceIds,
      @Param("q") String q,
      @Param("tag") String tag,
      Pageable pageable);
}
