package io.github.jhipster.sample.web.rest;

import io.github.jhipster.sample.config.McpContractProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the MCPHub login page ({@code GET /mcp-login}) and the domain-verification
 * well-known JSON file ({@code GET /.well-known/mcp-hub-verification.json}).
 */
@Controller
public class McpLoginPageController {

    private static final Logger LOG = LoggerFactory.getLogger(McpLoginPageController.class);

    private final McpContractProperties properties;

    public McpLoginPageController(McpContractProperties properties) {
        this.properties = properties;
    }

    /**
     * Renders the login page that MCPHub redirects users to.
     *
     * <p>Validates {@code callback_url} against the allowlist and
     * {@code code_challenge_method} before rendering the form. Returns an error page
     * — NOT a redirect — if validation fails (open-redirect defence).
     */
    @GetMapping("/mcp-login")
    public String loginPage(
        @RequestParam(name = "callback_url", required = false, defaultValue = "") String callbackUrl,
        @RequestParam(name = "state", required = false, defaultValue = "") String state,
        @RequestParam(name = "code_challenge", required = false, defaultValue = "") String codeChallenge,
        @RequestParam(name = "code_challenge_method", required = false, defaultValue = "") String codeChallengeMethod,
        @RequestParam(name = "tenant_slug", required = false, defaultValue = "") String tenantSlug,
        @RequestParam(name = "login_hint", required = false, defaultValue = "") String loginHint,
        Model model
    ) {
        // Open-redirect defence: validate callback_url before rendering any form
        if (callbackUrl.isBlank() || !properties.isAllowedCallbackUrl(callbackUrl)) {
            LOG.warn("MCP login page: callback_url not in allowlist: {}", callbackUrl);
            model.addAttribute("errorMessage", "The callback URL is not permitted for this tenant.");
            return "mcp/login";
        }

        // Reject unsupported PKCE methods (must be S256)
        if (!codeChallengeMethod.isBlank() && !"S256".equals(codeChallengeMethod)) {
            LOG.warn("MCP login page: unsupported code_challenge_method: {}", codeChallengeMethod);
            model.addAttribute("errorMessage", "Only code_challenge_method=S256 is supported.");
            return "mcp/login";
        }

        model.addAttribute("callbackUrl", callbackUrl);
        model.addAttribute("state", state);
        model.addAttribute("codeChallenge", codeChallenge);
        model.addAttribute("codeChallengeMethod", codeChallengeMethod.isBlank() ? "S256" : codeChallengeMethod);
        model.addAttribute("tenantSlug", tenantSlug);
        model.addAttribute("loginHint", loginHint);
        return "mcp/login";
    }

    /**
     * Serves the domain-verification well-known JSON file.
     *
     * <p>Only active if {@code mcp.contract.domain-verification-token} is configured.
     * Returns 404 otherwise.
     */
    @GetMapping(value = "/.well-known/mcp-hub-verification.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> domainVerification() {
        String token = properties.getDomainVerificationToken();
        if (token == null || token.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("token", token));
    }
}
