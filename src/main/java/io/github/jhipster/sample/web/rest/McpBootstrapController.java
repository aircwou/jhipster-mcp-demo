package io.github.jhipster.sample.web.rest;

import io.github.jhipster.sample.domain.User;
import io.github.jhipster.sample.repository.BankAccountRepository;
import io.github.jhipster.sample.repository.UserRepository;
import io.github.jhipster.sample.security.AuthoritiesConstants;
import io.github.jhipster.sample.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * MCPHub <em>enum injection</em> bootstrap endpoint.
 *
 * <p>The hub calls {@code GET /api/mcp/session/bootstrap} once per session (cached ~1h) to learn
 * which resource IDs the authenticated user is allowed to touch. The hub then injects a JSON-schema
 * {@code enum} constraint on every tool parameter marked {@code x-mcp-inject: "<key>"} in the
 * uploaded OpenAPI spec, so the AI client can't construct an out-of-scope call.
 *
 * <p>Authentication is the same as any tool call ({@code Authorization: Bearer mcp:<token>});
 * the existing {@code McpSessionAuthFilter} sets {@code SecurityContext} before this method runs.
 * Bootstrap responses are non-200 → the hub gracefully degrades (no enum injection).
 *
 * <p><b>Scope:</b> bank accounts only. <b>Admin policy:</b> if the user has {@code ROLE_ADMIN},
 * {@code roles} is returned empty so the hub injects no constraints — admins are unrestricted.
 */
@RestController
@Transactional(readOnly = true)
public class McpBootstrapController {

    private static final Logger LOG = LoggerFactory.getLogger(McpBootstrapController.class);

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;

    public McpBootstrapController(UserRepository userRepository, BankAccountRepository bankAccountRepository) {
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @GetMapping("/api/mcp/session/bootstrap")
    public Map<String, Object> bootstrap() {
        String login = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        User user = userRepository
            .findOneWithAuthoritiesByLogin(login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        boolean isAdmin = user
            .getAuthorities()
            .stream()
            .anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));

        List<Map<String, Object>> roles;
        if (isAdmin) {
            // Admins are unrestricted — empty roles list means no enum constraints are injected.
            roles = List.of();
        } else {
            // Single scoped query (SpEL resolves authentication.name from the SecurityContext
            // populated by McpSessionAuthFilter for the Bearer mcp: token).
            List<Long> bankAccountIds = bankAccountRepository
                .findByUserIsCurrentUser()
                .stream()
                .map(ba -> ba.getId())
                .toList();
            roles = user
                .getAuthorities()
                .stream()
                .map(a -> Map.<String, Object>of("role", a.getName(), "bankAccountIds", bankAccountIds))
                .toList();
        }

        LOG.debug("MCP bootstrap for user='{}' admin={} roles={}", login, isAdmin, roles.size());

        return Map.of(
            "email",
            user.getEmail() != null ? user.getEmail() : "",
            "accounts",
            List.of(Map.of("accountId", user.getId(), "roles", roles))
        );
    }
}
