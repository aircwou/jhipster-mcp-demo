package io.github.jhipster.sample.web.rest;

import io.github.jhipster.sample.config.McpContractProperties;
import io.github.jhipster.sample.domain.User;
import io.github.jhipster.sample.repository.AuthorityRepository;
import io.github.jhipster.sample.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional MCPHub directory-import endpoints.
 *
 * <p>MCPHub fetches these to auto-populate its member and role directories. Per the
 * contract (https://mcpweb.authio.ai/docs/endpoints) the hub sends an
 * <em>unauthenticated</em> {@code GET} — no HMAC, no bearer token — so both paths are
 * {@code permitAll} in {@code SecurityConfiguration}. Protect them at the proxy
 * (IP allowlist) rather than in the app, since adding auth would break the import.
 *
 * <p>The hub ignores responses with more than 500 users or 200 roles, so the lists
 * are capped accordingly.
 */
@RestController
@Transactional(readOnly = true)
public class McpDirectoryController {

    private static final Logger LOG = LoggerFactory.getLogger(McpDirectoryController.class);

    private static final int MAX_USERS = 500;
    private static final int MAX_ROLES = 200;

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final McpContractProperties properties;

    public McpDirectoryController(
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        McpContractProperties properties
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.properties = properties;
    }

    /**
     * {@code GET /api/mcp/users} : activated users with an email, as {@code [{email, name}]}.
     */
    @GetMapping("/api/mcp/users")
    public List<Map<String, String>> users() {
        LOG.debug("MCPHub directory import: list users");
        return userRepository
            .findAllByIdNotNullAndActivatedIsTrue(PageRequest.of(0, MAX_USERS))
            .stream()
            .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
            .map(user -> Map.of("email", user.getEmail(), "name", displayName(user)))
            .toList();
    }

    /**
     * {@code GET /api/mcp/roles} : authority names with their contract-v2 tool scopes,
     * as {@code [{name, toolScopes}]}.
     */
    @GetMapping("/api/mcp/roles")
    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> result = authorityRepository
            .findAll()
            .stream()
            .limit(MAX_ROLES)
            .map(authority ->
                Map.<String, Object>of(
                    "name",
                    authority.getName(),
                    "toolScopes",
                    properties.getToolScopes().getOrDefault(authority.getName(), List.of())
                )
            )
            .toList();
        // ⚠️ DEBUG ONLY — shows exactly what /api/mcp/roles returns to MCPHub.
        LOG.info("🧩 [MCP] /roles | toolScopeKeys(bound)={} | response={}", properties.getToolScopes().keySet(), result);
        return result;
    }

    private String displayName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String displayName = (firstName + " " + lastName).trim();
        return displayName.isBlank() ? user.getLogin() : displayName;
    }
}
