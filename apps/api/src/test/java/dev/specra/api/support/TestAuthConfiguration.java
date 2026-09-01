package dev.specra.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.specra.api.core.security.AuthenticatedUser;
import dev.specra.api.feature.auth.domain.User;
import dev.specra.api.feature.auth.domain.UserRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Signs every MockMvc request in as one real account.
 *
 * <p>The API is closed by default, so without this every integration test would assert on a 401
 * instead of on what it is about. Importing this is the equivalent of "a user is signed in" — the
 * precondition of nearly every test, stated once instead of on every {@code perform}.
 *
 * <p>The user is a real row, not a mocked principal: services load it ({@code WorkspaceService}
 * needs one to own a workspace) and a membership has a foreign key to it. It is created on the
 * first request rather than at startup, because at bean-creation time Flyway may not have run.
 *
 * <p>A test that is <em>about</em> being signed out overrides this per request with {@code
 * .with(anonymous())}, which wins over the default.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuthConfiguration {

  public static final String EMAIL = "integration@specra.dev";
  public static final String DISPLAY_NAME = "Integration Test";

  /** Never used to sign in — the tests bypass the login endpoint — but the column is not null. */
  private static final String PASSWORD = "integration-test-password";

  private final AtomicReference<AuthenticatedUser> cached = new AtomicReference<>();

  @Bean
  MockMvcBuilderCustomizer authenticateEveryRequest(
      UserRepository users, PasswordEncoder passwords) {
    RequestPostProcessor asTestUser =
        request -> {
          AuthenticatedUser principal = ensureUser(users, passwords);
          return SecurityMockMvcRequestPostProcessors.authentication(
                  new UsernamePasswordAuthenticationToken(
                      principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))
              .postProcessRequest(request);
        };
    return builder -> builder.defaultRequest(get("/").with(asTestUser));
  }

  private AuthenticatedUser ensureUser(UserRepository users, PasswordEncoder passwords) {
    AuthenticatedUser existing = cached.get();
    if (existing != null) {
      return existing;
    }
    User user =
        users
            .findByEmailIgnoringCase(EMAIL)
            .orElseGet(
                () -> {
                  User created = new User();
                  created.setEmail(EMAIL);
                  created.setDisplayName(DISPLAY_NAME);
                  created.setPasswordHash(passwords.encode(PASSWORD));
                  return users.save(created);
                });
    AuthenticatedUser principal =
        new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName());
    cached.set(principal);
    return principal;
  }
}
