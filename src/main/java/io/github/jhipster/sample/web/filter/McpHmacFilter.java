package io.github.jhipster.sample.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jhipster.sample.security.mcp.HmacVerificationService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet filter that verifies HMAC-SHA256 signatures on MCPHub server-to-server requests.
 *
 * <p>Applied to {@code /api/mcp/exchange}, {@code /api/mcp/validate},
 * {@code /api/mcp/refresh}, and {@code /api/mcp/logout} by {@link McpHmacFilter}.
 *
 * <p>The raw request body is read and cached so it can be re-read by the downstream
 * controller via {@link RepeatableReadRequestWrapper}.
 */
public class McpHmacFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(McpHmacFilter.class);
    private static final String SUPPORTED_CONTRACT_VERSION = "2";

    private final HmacVerificationService hmacVerificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpHmacFilter(HmacVerificationService hmacVerificationService) {
        this.hmacVerificationService = hmacVerificationService;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Read and cache the body so it can be consumed again downstream
        byte[] rawBody;
        try {
            rawBody = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            LOG.warn("Failed to read request body for HMAC verification", e);
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Failed to read request body");
            return;
        }

        // Build the full URL using X-Forwarded-* headers if present (Cloudflare tunnel).
        // MCPHub signs over the public URL (e.g. https://jhipster-raw.authio.ai/api/mcp/validate),
        // so we must reconstruct it the same way.
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host =
                request.getServerName() +
                (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "");
        }
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = scheme + "://" + host + requestUri + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");

        // Extract HMAC headers
        String timestamp = request.getHeader("X-Hub-Timestamp");
        String nonce = request.getHeader("X-Hub-Nonce");
        String signature = request.getHeader("X-Hub-Signature-256");

        HmacVerificationService.Result result = hmacVerificationService.verify(
            request.getMethod(),
            fullUrl,
            rawBody,
            timestamp,
            nonce,
            signature
        );

        if (!result.ok()) {
            // Log the specific reason server-side, but return a generic message so we
            // don't reveal to the caller why verification failed.
            LOG.debug("HMAC verification failed for {}: {}", fullUrl, result.error());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
            return;
        }

        // Contract version guard (v2): the hub sends X-Hub-Contract-Version on every
        // server-to-server call. Reject a recognised-but-unsupported version rather than
        // silently assuming v1. An absent header is allowed for leniency.
        String contractVersion = request.getHeader("X-Hub-Contract-Version");
        if (contractVersion != null && !SUPPORTED_CONTRACT_VERSION.equals(contractVersion)) {
            LOG.debug("Unsupported X-Hub-Contract-Version '{}' for {}", contractVersion, fullUrl);
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "unsupported_contract_version");
            return;
        }

        // Wrap request so the cached body can be read again by the controller
        chain.doFilter(new RepeatableReadRequestWrapper(request, rawBody), response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", message)));
    }

    /**
     * Wraps an {@link HttpServletRequest} so that its body can be read more than once.
     */
    static class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        RepeatableReadRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op for synchronous filter
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
