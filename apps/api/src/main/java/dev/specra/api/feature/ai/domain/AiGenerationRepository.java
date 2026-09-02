package dev.specra.api.feature.ai.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {

  List<AiGeneration> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(
      String subjectType, UUID subjectId);
}
