package io.github.jhipster.sample.web.rest;

import io.github.jhipster.sample.config.McpContractProperties;
import io.github.jhipster.sample.security.DomainUserDetailsService;
import io.github.jhipster.sample.security.mcp.McpAuthCodeStore;
import io.github.jhipster.sample.service.McpEnumScopesService;
import io.github.jhipster.sample.service.McpSessionService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing the five MCPHub TENANT_DELEGATED contract endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/mcp/login} — browser form submission; issues auth code, redirects</li>
 *   <li>{@code POST /api/mcp/exchange} — HMAC-signed; code → session token</li>
 *   <li>{@code POST /api/mcp/validate} — HMAC-signed; validate session token</li>
 *   <li>{@code POST /api/mcp/refresh} — HMAC-signed; rotate session token</li>
 *   <li>{@code POST /api/mcp/logout} — HMAC-signed; revoke session token</li>
 * </ul>
 *
 * <p>HMAC verification for the four signed endpoints is handled upstream by
 * {@link io.github.jhipster.sample.web.filter.McpHmacFilter}.
 */
@RestController
public class McpContractController {

    private static final Logger LOG = LoggerFactory.getLogger(McpContractController.class);

    /** Regex for a valid PKCE S256 challenge: exactly 43 unpadded base64url characters. */
    private static final Pattern CODE_CHALLENGE_RE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final McpContractProperties properties;
    private final McpAuthCodeStore authCodeStore;
    private final McpSessionService sessionService;
    private final McpEnumScopesService enumScopesService;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    public McpContractController(
        McpContractProperties properties,
        McpAuthCodeStore authCodeStore,
        McpSessionService sessionService,
        McpEnumScopesService enumScopesService,
        AuthenticationManagerBuilder authenticationManagerBuilder
    ) {
        this.properties = properties;
        this.authCodeStore = authCodeStore;
        this.sessionService = sessionService;
        this.enumScopesService = enumScopesService;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
    }

    // -----------------------------------------------------------------------
    // POST /api/mcp/login — browser form POST
    // -----------------------------------------------------------------------

    /**
     * Authenticates the user from the MCP login form, issues an auth code, and redirects
     * back to the hub's callback URL.
     *
     * <p>This endpoint is {@code application/x-www-form-urlencoded}. No HMAC is required.
     */
    @PostMapping(value = "/api/mcp/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> handleLogin(
        @RequestParam(name = "username", required = false) String username,
        @RequestParam(name = "password", required = false) String password,
        @RequestParam(name = "callback_url", required = false) String callbackUrl,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "code_challenge", required = false) String codeChallenge
    ) {
        // ⚠️ DEBUG ONLY — the issued auth code appears in the success Location URL.
        // It is short-lived (60s) and consumed once by /exchange, but anyone with log
        // access could replay it within that window. Demote to DEBUG when stable.
        LOG.info(
            "🔐 [MCP-login] ➡️  POST /api/mcp/login | username={} callback_url={} state={} code_challenge={}",
            username,
            callbackUrl,
            state,
            codeChallenge
        );

        // Validate required fields — if callbackUrl missing/disallowed, return error page rather than redirect
        if (callbackUrl == null || callbackUrl.isBlank() || !properties.isAllowedCallbackUrl(callbackUrl)) {
            LOG.warn("MCP login rejected: callback_url not in allowlist: {}", callbackUrl);
            LOG.info("🔐 [MCP-login] ⬅️  400 Bad Request (reason: bad_callback_url)");
            return ResponseEntity.badRequest().build();
        }

        // Redirect back with error on any validation failure below
        String errorRedirect = callbackUrl + "?state=" + encodeParam(state) + "&error=access_denied";

        if (state == null || state.isBlank()) {
            LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (reason: missing_state)", errorRedirect);
            return redirect(errorRedirect);
        }
        if (codeChallenge == null || !CODE_CHALLENGE_RE.matcher(codeChallenge).matches()) {
            LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (reason: bad_code_challenge)", errorRedirect);
            return redirect(errorRedirect);
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (reason: missing_credentials)", errorRedirect);
            return redirect(errorRedirect);
        }

        // Authenticate the user
        Long userId;
        try {
            var token = new UsernamePasswordAuthenticationToken(username.toLowerCase(), password);
            var authentication = authenticationManagerBuilder.getObject().authenticate(token);
            if (authentication.getPrincipal() instanceof DomainUserDetailsService.UserWithId principal) {
                userId = principal.getId();
            } else {
                LOG.warn("MCP login: unexpected principal type {}", authentication.getPrincipal().getClass());
                LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (reason: unexpected_principal)", errorRedirect);
                return redirect(errorRedirect);
            }
        } catch (Exception e) {
            LOG.debug("MCP login authentication failed for user {}: {}", username, e.getMessage());
            LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (reason: auth_failed)", errorRedirect);
            return redirect(errorRedirect);
        }

        // Issue auth code and redirect to hub
        String code = authCodeStore.issueAuthCode(userId, codeChallenge);
        String successRedirect = callbackUrl + "?state=" + encodeParam(state) + "&code=" + encodeParam(code);
        LOG.debug("MCP login succeeded for user {}; redirecting with auth code", userId);
        LOG.info("🔐 [MCP-login] ⬅️  302 Location={} (userId={})", successRedirect, userId);
        return redirect(successRedirect);
    }

    // -----------------------------------------------------------------------
    // POST /api/mcp/exchange — HMAC-signed
    // -----------------------------------------------------------------------

    /**
     * Exchanges an auth code and PKCE verifier for a session token.
     */
    @PostMapping(value = "/api/mcp/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String codeVerifier = body.get("code_verifier");

        if (code == null || codeVerifier == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }

        Long userId = authCodeStore.consumeAuthCode(code, codeVerifier);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_code"));
        }

        int ttl = properties.getSessionTtlSec();
        McpSessionService.SessionResult session = sessionService.createSession(userId, ttl);
        List<String> roles = sessionService.getUserRoles(userId);

        // Fetch user email and display name for the response
        String email = "";
        String displayName = "";
        try {
            var userOpt = sessionService.getUserInfo(userId);
            if (userOpt != null) {
                email = userOpt.email();
                displayName = userOpt.displayName();
            }
        } catch (Exception e) {
            LOG.warn("Could not fetch user info for MCP exchange response: {}", e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user_id", userId.toString());
        response.put("email", email);
        response.put("session_token", session.token());
        response.put("expires_in", session.expiresIn());
        response.put("roles", roles);
        response.put("toolScopes", properties.toolScopesForRoles(roles));
        response.put("display_name", displayName);
        putEnumScopes(response, userId);
        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // POST /api/mcp/validate — HMAC-signed
    // -----------------------------------------------------------------------

    /**
     * Validates a session token and returns remaining TTL and roles.
     */
    @PostMapping(value = "/api/mcp/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
        String sessionToken = body.get("session_token");

        if (sessionToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }

        var sessionOpt = sessionService.findSession(sessionToken);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("valid", false, "remaining_seconds", 0));
        }

        var session = sessionOpt.get();
        long remainingMs = session.getExpiresAt().toEpochMilli() - System.currentTimeMillis();
        int remainingSeconds = (int) Math.max(0, remainingMs / 1000L);
        List<String> roles = sessionService.getUserRoles(session.getUserId());

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("remaining_seconds", remainingSeconds);
        response.put("roles", roles);
        response.put("toolScopes", properties.toolScopesForRoles(roles));
        putEnumScopes(response, session.getUserId());
        return ResponseEntity.ok(response);
    }

    // -----------------------------------------------------------------------
    // POST /api/mcp/refresh — HMAC-signed
    // -----------------------------------------------------------------------

    /**
     * Atomically rotates a session token.
     */
    @PostMapping(value = "/api/mcp/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String sessionToken = body.get("session_token");

        if (sessionToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }

        int ttl = properties.getSessionTtlSec();
        var result = sessionService.refreshSession(sessionToken, ttl);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "session_expired"));
        }

        return ResponseEntity.ok(Map.of("session_token", result.get().token(), "expires_in", result.get().expiresIn()));
    }

    // -----------------------------------------------------------------------
    // POST /api/mcp/logout — HMAC-signed
    // -----------------------------------------------------------------------

    /**
     * Revokes a session token. Idempotent.
     */
    @PostMapping(value = "/api/mcp/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String sessionToken = body.get("session_token");

        if (sessionToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing_fields"));
        }

        sessionService.revokeSession(sessionToken);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Adds the record-scope payload LockMCP stores on the session ({@link McpEnumScopesService}).
     * Never fails the exchange or validate: without it LockMCP keeps what it has (validate) or
     * refuses scoped calls until a payload arrives (exchange).
     */
    private void putEnumScopes(Map<String, Object> response, Long userId) {
        try {
            Map<String, Object> scopes = enumScopesService.forUser(userId);
            if (scopes != null) {
                response.put("enum_scopes", scopes);
            }
        } catch (Exception e) {
            LOG.warn("Could not build enum_scopes for user {}: {}", userId, e.getMessage());
        }
    }

    private ResponseEntity<Void> redirect(String url) {
        try {
            return ResponseEntity.status(HttpStatus.FOUND).location(new URI(url)).build();
        } catch (URISyntaxException e) {
            LOG.error("Invalid redirect URI: {}", url, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private String encodeParam(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
