package io.github.jhipster.sample.security.mcp;

import io.github.jhipster.sample.config.McpContractProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory store for short-lived, single-use auth codes issued during the MCPHub login flow.
 *
 * <p>Each code is 32 random bytes encoded as unpadded Base64URL (~43 characters).
 * Codes expire after {@code mcp.contract.auth-code-ttl-sec} seconds (default 60) and
 * are consumed (deleted) on first successful use.
 */
@Component
public class McpAuthCodeStore {

    private static final Logger LOG = LoggerFactory.getLogger(McpAuthCodeStore.class);
    private static final String PKCE_DIGEST_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final McpContractProperties properties;

    /** code → record mapping. */
    private final Map<String, CodeRecord> codes = new ConcurrentHashMap<>();

    public McpAuthCodeStore(McpContractProperties properties) {
        this.properties = properties;
    }

    private record CodeRecord(Long userId, String codeChallenge, long expiresAtMs) {}

    /**
     * Issues a new auth code for the given user and PKCE challenge.
     *
     * @param userId        the authenticated user's database ID
     * @param codeChallenge the PKCE S256 challenge (43-char base64url)
     * @return the opaque auth code to embed in the redirect URL
     */
    public String issueAuthCode(Long userId, String codeChallenge) {
        sweep();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long ttlMs = (long) properties.getAuthCodeTtlSec() * 1000L;
        codes.put(code, new CodeRecord(userId, codeChallenge, System.currentTimeMillis() + ttlMs));
        return code;
    }

    /**
     * Consumes an auth code if it is valid, unexpired, and the PKCE verifier matches.
     *
     * <p>Successful consumption removes the code (single-use).
     *
     * @param code         the auth code from the hub's exchange request
     * @param codeVerifier the PKCE verifier whose SHA-256 must equal the stored challenge
     * @return the user ID if the code is valid, or {@code null} otherwise
     */
    public Long consumeAuthCode(String code, String codeVerifier) {
        sweep();
        if (code == null || codeVerifier == null) {
            return null;
        }

        CodeRecord record = codes.get(code);
        if (record == null || record.expiresAtMs() <= System.currentTimeMillis()) {
            codes.remove(code);
            return null;
        }

        // PKCE S256: BASE64URL(SHA-256(verifier)) must equal stored challenge
        try {
            MessageDigest digest = MessageDigest.getInstance(PKCE_DIGEST_ALGORITHM);
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            if (!computed.equals(record.codeChallenge())) {
                LOG.debug("PKCE verifier mismatch for auth code exchange");
                return null;
            }
        } catch (NoSuchAlgorithmException e) {
            LOG.error("SHA-256 not available", e);
            return null;
        }

        // Single-use: remove before returning
        codes.remove(code);
        return record.userId();
    }

    /** Removes expired codes. Runs every 60 seconds. */
    @Scheduled(fixedRate = 60_000)
    public void sweep() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }
}
