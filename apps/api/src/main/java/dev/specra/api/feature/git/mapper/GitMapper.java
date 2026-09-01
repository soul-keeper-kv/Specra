package dev.specra.api.feature.git.mapper;

import dev.specra.api.core.git.CommitInfo;
import dev.specra.api.core.git.FileChange;
import dev.specra.api.core.git.GitStatus;
import dev.specra.api.feature.git.domain.GitRepository;
import dev.specra.api.feature.git.dto.CommitInfoResponse;
import dev.specra.api.feature.git.dto.GitStatusResponse;
import dev.specra.api.feature.git.dto.RepositoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Port types and the entity into the wire shapes. Nothing on the way in: connecting a repository
 * evicts caches and re-points a remote, which is a rule, not a field copy.
 *
 * <p>The methods have distinct names rather than three {@code toResponse} overloads, because an
 * overloaded method has no usable method reference — {@code mapper::toResponse} inside a generic
 * call resolves against {@code Object} and fails to compile.
 */
@Mapper(componentModel = "spring")
public interface GitMapper {

  @Mapping(target = "connectedAt", source = "createdAt")
  RepositoryResponse toRepository(GitRepository repository);

  @Mapping(target = "clean", expression = "java(status.clean())")
  GitStatusResponse toStatus(GitStatus status);

  GitStatusResponse.Change toChange(FileChange change);

  CommitInfoResponse toCommit(CommitInfo commit);
}
