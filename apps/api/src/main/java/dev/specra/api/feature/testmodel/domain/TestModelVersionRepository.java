package dev.specra.api.feature.testmodel.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestModelVersionRepository extends JpaRepository<TestModelVersion, UUID> {

  Optional<TestModelVersion> findFirstByTestCaseIdOrderByVersionDesc(UUID testCaseId);

  Optional<TestModelVersion> findByTestCaseIdAndVersion(UUID testCaseId, int version);

  List<TestModelVersion> findByTestCaseIdOrderByVersionDesc(UUID testCaseId);
}
