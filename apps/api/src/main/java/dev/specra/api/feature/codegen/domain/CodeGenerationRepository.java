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

  /**
   * The last proposal that actually reached the repository.
   *
   * <p>Its {@code testModelId} is the IR version the committed code was projected from, which is
   * the only honest baseline for "what changed since": a superseded or rejected proposal was never
   * in anyone's repository, so diffing against one would describe a change that never happened.
   */
  Optional<CodeGeneration> findFirstByTestCaseIdAndStatusOrderByDecidedAtDesc(
      UUID testCaseId, CodeGenerationStatus status);
}
