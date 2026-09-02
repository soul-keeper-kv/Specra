package dev.specra.api.feature.codegen.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeGenerationRepository extends JpaRepository<CodeGeneration, UUID> {

  List<CodeGeneration> findByTestCaseIdOrderByCreatedAtDesc(UUID testCaseId);

  Optional<CodeGeneration> findFirstByTestCaseIdAndStatusOrderByCreatedAtDesc(
      UUID testCaseId, CodeGenerationStatus status);

  List<CodeGeneration> findByTestCaseIdAndStatus(UUID testCaseId, CodeGenerationStatus status);
}
