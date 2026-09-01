package dev.specra.api.feature.auth.mapper;

import dev.specra.api.feature.auth.domain.RefreshToken;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.dto.AccountResponse;
import dev.specra.api.feature.auth.dto.SessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to response only. There is no {@code toEntity}: creating a user means hashing a password
 * and normalising an address, which is a rule and therefore belongs in {@code AuthService} — a
 * generated mapper that could build a {@link User} would be a way to get one without either.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

  AccountResponse toResponse(User user);

  @Mapping(target = "current", source = "current")
  SessionResponse toSession(RefreshToken token, boolean current);
}
