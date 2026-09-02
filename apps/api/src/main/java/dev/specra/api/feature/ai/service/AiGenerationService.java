package dev.specra.api.feature.ai.service;

import dev.specra.api.feature.ai.domain.AiGeneration;
import dev.specra.api.feature.ai.domain.AiGenerationRepository;
import dev.specra.api.feature.ai.dto.AiGenerationRecord;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit trail every pipeline step writes to. Deliberately small: the rules about what may be
 * proposed and who may apply it live with the step that proposes, and this only guarantees the row
 * exists whatever else happens.
 */
@Service
public class AiGenerationService {

  private final AiGenerationRepository repository;

  public AiGenerationService(AiGenerationRepository repository) {
    this.repository = repository;
  }

  /** Its own transaction on purpose: a step that then fails must still leave its audit line. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID record(AiGenerationRecord record) {
    AiGeneration generation = new AiGeneration();
    generation.setWorkspaceId(record.workspaceId());
    generation.setProjectId(record.projectId());
    generation.setKind(record.kind());
    generation.setStatus(record.status());
    generation.setSubjectType(record.subjectType());
    generation.setSubjectId(record.subjectId());
    generation.setResultId(record.resultId());
    generation.setInputChecksum(record.inputChecksum());
    generation.setProvider(record.provider());
    generation.setModel(record.model());
    generation.setPromptTokens(record.promptTokens());
    generation.setCompletionTokens(record.completionTokens());
    generation.setLatencyMs(record.latencyMs());
    generation.setRationale(record.rationale());
    return repository.save(generation).getId();
  }
}
