package dev.specra.api.feature.pageobject.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageObjectRepository extends JpaRepository<PageObject, UUID> {

  List<PageObject> findByProjectIdOrderByNameAsc(UUID projectId);

  Optional<PageObject> findByProjectIdAndName(UUID projectId, String name);
}
