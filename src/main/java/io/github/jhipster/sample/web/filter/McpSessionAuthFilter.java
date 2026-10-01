package io.github.jhipster.sample.web.filter;

import io.github.jhipster.sample.service.McpSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that authenticates requests carrying an MCP session token.
 *
 * <p>When MCPHub forwards tool calls to this tenant app it sends the delegated
 * session token as {@code Authorization: Bearer mcp:<hex>}.  Spring Security's
 * built-in JWT filter cannot validate this opaque token, so it would reject the
 * request with HTTP 401 before any business logic runs.
 *
 * <p>This filter intercepts such requests <em>before</em> the JWT filter, validates
 * the {@code mcp:} token against {@link McpSessionService}, and — if valid —
 * populates the {@link SecurityContextHolder} with the user's authentication so
 * the rest of the chain proceeds normally.
 */
public class McpSessionAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(McpSessionAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MCP_TOKEN_PREFIX = "mcp:";

    private final McpSessionService sessionService;

    public McpSessionAuthFilter(McpSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        boolean hasBearer = authHeader != null && authHeader.startsWith(BEARER_PREFIX);
        LOG.info("🛰️  [MCP] ➡️  {} {} | bearer={}", request.getMethod(), request.getRequestURI(), hasBearer);

        if (hasBearer) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            boolean isMcp = token.startsWith(MCP_TOKEN_PREFIX);
            // ⚠️ DEBUG ONLY — leaks full session token. Remove before any production deploy.
            LOG.info("🔑 [MCP] access token (raw): {}", token);
            LOG.info("🔍 [MCP] token kind: {}", isMcp ? "mcp: ✅" : "non-mcp ⏭️  (will pass to JWT)");
            if (isMcp) {
                var sessionOpt = sessionService.findSession(token);
                LOG.info("🗄️  [MCP] session lookup: {}", sessionOpt.isPresent() ? "HIT 🎯" : "MISS ❌");
                if (sessionOpt.isPresent()) {
                    Long userId = sessionOpt.get().getUserId();
                    String login = sessionService.getLoginById(userId).orElse("mcp-user-" + userId);
                    List<String> roles = sessionService.getUserRoles(userId);
                    LOG.info("👤 [MCP] authenticated user='{}' 🛡️  roles={}", login, roles);
                    var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
                    var auth = new UsernamePasswordAuthenticationToken(login, null, authorities);
                    // Spring Security 6: must create a new context and SET it explicitly.
                    // getContext().setAuthentication() mutates a DeferredSecurityContext that
                    // may be replaced later in the filter chain, silently dropping our auth.
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(auth);
                    SecurityContextHolder.setContext(context);
                    LOG.info("🔐 [MCP] SecurityContext set ✅ — handing off to chain ⏩");
                } else {
                    // An mcp:-prefixed token was presented but did not match an active session.
                    // Stop here with a structured 401 so the hub can call /api/mcp/refresh
                    // instead of receiving Spring's bodyless default.
                    LOG.warn("🚫 [MCP] session expired or unknown — returning 401 session_expired 📤");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":\"session_expired\"}");
                    LOG.info("🛰️  [MCP] ⬅️  {} {} | status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
        LOG.info("🛰️  [MCP] ⬅️  {} {} | status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
    }
}
