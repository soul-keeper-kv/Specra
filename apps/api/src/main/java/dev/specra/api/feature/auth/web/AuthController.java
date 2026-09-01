package dev.specra.api.feature.auth.web;

import dev.specra.api.feature.auth.dto.AccountResponse;
import dev.specra.api.feature.auth.dto.AuthTokens;
import dev.specra.api.feature.auth.dto.ChangePasswordRequest;
import dev.specra.api.feature.auth.dto.LoginRequest;
import dev.specra.api.feature.auth.dto.ProfileRequest;
import dev.specra.api.feature.auth.dto.RefreshRequest;
import dev.specra.api.feature.auth.dto.RegisterRequest;
import dev.specra.api.feature.auth.dto.SessionResponse;
import dev.specra.api.feature.auth.service.AuthService;
import dev.specra.api.feature.auth.service.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up, sign-in and everything an account does about itself.
 *
 * <p>Only the first three endpoints are reachable without a token; the rest are behind the filter
 * chain like the rest of the API. The refresh token is carried in the body rather than in a header
 * because a body is not written to an access log, and this is the one credential in the system with
 * a lifetime measured in days.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Auth",
    description =
        "Accounts and sessions. A short-lived signed access token authenticates every other "
            + "endpoint; the refresh token is opaque, stored hashed, and rotated on every use.")
public class AuthController {

  /**
   * Lets {@code GET /sessions} mark the caller's own device without putting a long-lived credential
   * in a query string, where it would land in every access log between here and the browser.
   */
  private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  @Operation(summary = "Create an account and sign in; the new user gets their own workspace")
  public ResponseEntity<AuthTokens> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request, client(http)));
  }

  @PostMapping("/login")
  @Operation(summary = "Exchange an email and password for an access and refresh token pair")
  public AuthTokens login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return service.login(request, client(http));
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Exchange a refresh token for a new pair",
      description =
          "The presented token is invalidated. Presenting one that has already been exchanged "
              + "signs every device out, because two parties holding it is indistinguishable "
              + "from a theft.")
  public AuthTokens refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
    return service.refresh(request.refreshToken(), client(http));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke one refresh token; the access token expires on its own")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
    service.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @Operation(summary = "The signed-in account")
  public AccountResponse me() {
    return service.me();
  }

  @PatchMapping("/me")
  @Operation(summary = "Change display name and preferred language; the email is not editable here")
  public AccountResponse updateProfile(@Valid @RequestBody ProfileRequest request) {
    return service.updateProfile(request);
  }

  @PostMapping("/change-password")
  @Operation(
      summary = "Set a new password",
      description = "Every other device is signed out, and this one is issued a fresh pair.")
  public AuthTokens changePassword(
      @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
    return service.changePassword(request, client(http));
  }

  @GetMapping("/sessions")
  @Operation(summary = "Devices with a live refresh token")
  public List<SessionResponse> sessions(
      @Parameter(description = "Send the caller's refresh token to have its own session marked")
          @RequestHeader(name = REFRESH_TOKEN_HEADER, required = false)
          @Nullable String presented) {
    return service.sessions(presented);
  }

  @DeleteMapping("/sessions/{id}")
  @Operation(summary = "Sign one device out")
  public ResponseEntity<Void> revokeSession(@PathVariable UUID id) {
    service.revokeSession(id);
    return ResponseEntity.noContent().build();
  }

  private static ClientInfo client(HttpServletRequest request) {
    return ClientInfo.of(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
  }
}
