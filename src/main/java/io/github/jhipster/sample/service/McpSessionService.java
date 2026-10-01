package io.github.jhipster.sample.service;

import io.github.jhipster.sample.config.McpContractProperties;
import io.github.jhipster.sample.domain.McpSession;
import io.github.jhipster.sample.repository.McpSessionRepository;
import io.github.jhipster.sample.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages MCPHub session tokens.
 *
 * <p>Session token format: {@code mcp:} + 64 lowercase hex chars (68 total).
 */
@Service
@Transactional
public class McpSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(McpSessionService.class);
    private static final String TOKEN_PREFIX = "mcp:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final McpSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final McpContractProperties properties;

    public McpSessionService(McpSessionRepository sessionRepository, UserRepository userRepository, McpContractProperties properties) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public record SessionResult(String token, int expiresIn) {}

    public record UserInfo(String email, String displayName) {}

    /**
     * Creates and persists a new session token for the given user.
     *
     * @param userId the database ID of the authenticated user
     * @param ttlSec lifetime in seconds
     * @return the new token and TTL
     */
    public SessionResult createSession(Long userId, int ttlSec) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plusSeconds(ttlSec);
        sessionRepository.save(new McpSession(token, userId, expiresAt));
        LOG.debug("Created MCP session for user {}", userId);
        return new SessionResult(token, ttlSec);
    }

    /**
     * Looks up an unexpired session by token.
     *
     * @param token the session token (must start with {@code mcp:})
     * @return the session if found and not expired, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<McpSession> findSession(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        return sessionRepository.findByToken(token).filter(s -> s.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Atomically rotates a session token: deletes the old token and creates a new one.
     *
     * @param oldToken the current session token to replace
     * @param ttlSec   lifetime for the new token in seconds
     * @return the new token and TTL, or empty if the old token is invalid/expired
     */
    public Optional<SessionResult> refreshSession(String oldToken, int ttlSec) {
        if (oldToken == null || !oldToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }

        Optional<McpSession> existing = sessionRepository.findByToken(oldToken);
        if (existing.isEmpty() || !existing.get().getExpiresAt().isAfter(Instant.now())) {
            // Token missing or expired — delete defensively and return empty
            sessionRepository.deleteByToken(oldToken);
            return Optional.empty();
        }

        Long userId = existing.get().getUserId();

        // Verify the user still exists
        if (userRepository.findById(userId).isEmpty()) {
            sessionRepository.deleteByToken(oldToken);
            return Optional.empty();
        }

        // Atomic swap within the current transaction
        sessionRepository.deleteByToken(oldToken);

        String newToken = generateToken();
        Instant expiresAt = Instant.now().plusSeconds(ttlSec);
        sessionRepository.save(new McpSession(newToken, userId, expiresAt));
        LOG.debug("Refreshed MCP session for user {}", userId);
        return Optional.of(new SessionResult(newToken, ttlSec));
    }

    /**
     * Revokes (deletes) a session token. Idempotent — safe to call for unknown tokens.
     *
     * @param token the session token to revoke
     */
    public void revokeSession(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return;
        }
        sessionRepository.deleteByToken(token);
    }

    /**
     * Returns the authority names of the given user, used as MCP roles.
     *
     * @param userId the user's database ID
     * @return list of authority name strings, e.g. {@code ["ROLE_USER", "ROLE_ADMIN"]}
     */
    @Transactional(readOnly = true)
    public List<String> getUserRoles(Long userId) {
        return userRepository
            .findById(userId)
            .map(user ->
                user
                    .getAuthorities()
                    .stream()
                    .map(a -> a.getName())
                    .collect(Collectors.toList())
            )
            .orElse(List.of());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return TOKEN_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Returns the login (username) for the given user.
     *
     * @param userId the user's database ID
     * @return the login, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<String> getLoginById(Long userId) {
        return userRepository.findById(userId).map(user -> user.getLogin());
    }

    /**
     * Returns email and display name for the given user.
     *
     * @param userId the user's database ID
     * @return a {@link UserInfo} record, or {@code null} if the user is not found
     */
    @Transactional(readOnly = true)
    public UserInfo getUserInfo(Long userId) {
        return userRepository
            .findById(userId)
            .map(user -> {
                String firstName = user.getFirstName() != null ? user.getFirstName() : "";
                String lastName = user.getLastName() != null ? user.getLastName() : "";
                String displayName = (firstName + " " + lastName).trim();
                if (displayName.isBlank()) {
                    displayName = user.getLogin();
                }
                return new UserInfo(user.getEmail() != null ? user.getEmail() : "", displayName);
            })
            .orElse(null);
    }
}
