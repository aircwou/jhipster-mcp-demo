package io.github.jhipster.sample.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the MCPHub TENANT_DELEGATED integration contract.
 *
 * <p>Values are bound from environment variables via the {@code mcp.contract} prefix in
 * {@code application.yml}.
 */
// Note: ignoreUnknownFields stays at its default (true). Setting it to false is
// incompatible with the tool-scopes Map below — Spring's strict binder reports map
// entries (e.g. tool-scopes[ROLE_ADMIN]) as "left unbound" and aborts startup.
@Component
@ConfigurationProperties(prefix = "mcp.contract")
public class McpContractProperties {

    /**
     * 64-character hex HMAC shared secret provided by the hub admin.
     * Maps to env var {@code MCP_CONTRACT_HMAC_KEY}.
     */
    private String hmacKey = "";

    /**
     * Session token lifetime in seconds (default 3600).
     * Maps to env var {@code MCP_CONTRACT_SESSION_TTL_SEC}.
     */
    private int sessionTtlSec = 3600;

    /**
     * Auth code lifetime in seconds (default 60).
     * Maps to env var {@code MCP_CONTRACT_AUTH_CODE_TTL_SEC}.
     */
    private int authCodeTtlSec = 60;

    /**
     * Comma-separated allowlist of hub callback hosts (e.g. {@code mcpweb.authio.ai}).
     * Maps to env var {@code hmac}.
     */
    private List<String> allowedCallbackHosts = new ArrayList<>();

    /**
     * Optional domain-verification token for the well-known JSON endpoint.
     * Maps to env var {@code MCP_CONTRACT_DOMAIN_VERIFICATION_TOKEN}.
     */
    private String domainVerificationToken = "";

    /**
     * Contract-v2 tool scopes: maps a Spring authority name (e.g. {@code ROLE_ADMIN}) to the list of
     * OpenAPI {@code operationId}s that role may invoke. Bound from {@code mcp.contract.tool-scopes}.
     * Authorities absent from the map have no tool scopes (empty list).
     */
    private Map<String, List<String>> toolScopes = new HashMap<>();

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public int getSessionTtlSec() {
        return sessionTtlSec;
    }

    public void setSessionTtlSec(int sessionTtlSec) {
        this.sessionTtlSec = sessionTtlSec;
    }

    public int getAuthCodeTtlSec() {
        return authCodeTtlSec;
    }

    public void setAuthCodeTtlSec(int authCodeTtlSec) {
        this.authCodeTtlSec = authCodeTtlSec;
    }

    public List<String> getAllowedCallbackHosts() {
        return allowedCallbackHosts;
    }

    public void setAllowedCallbackHosts(List<String> allowedCallbackHosts) {
        this.allowedCallbackHosts = allowedCallbackHosts;
    }

    public String getDomainVerificationToken() {
        return domainVerificationToken;
    }

    public void setDomainVerificationToken(String domainVerificationToken) {
        this.domainVerificationToken = domainVerificationToken;
    }

    public Map<String, List<String>> getToolScopes() {
        return toolScopes;
    }

    public void setToolScopes(Map<String, List<String>> toolScopes) {
        this.toolScopes = toolScopes;
    }

    /**
     * Returns the union of tool scopes (OpenAPI {@code operationId}s) granted to the given roles,
     * deduplicated and sorted. Returns an empty list when no role maps to any scope.
     */
    public List<String> toolScopesForRoles(Collection<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles
            .stream()
            .flatMap(role -> toolScopes.getOrDefault(role, List.of()).stream())
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Returns {@code true} if the host portion of {@code url} is in the allowed callback hosts list.
     */
    public boolean isAllowedCallbackUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = new java.net.URI(url).getHost();
            return host != null && allowedCallbackHosts.contains(host);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
