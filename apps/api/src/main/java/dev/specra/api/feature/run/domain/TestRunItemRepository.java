package dev.specra.api.feature.run.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunItemRepository extends JpaRepository<TestRunItem, UUID> {}
