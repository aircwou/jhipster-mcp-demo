package io.github.jhipster.sample.security.mcp;

import io.github.jhipster.sample.config.McpContractProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verifies HMAC-SHA256 signatures sent by MCPHub on server-to-server requests.
 *
 * <p>The string-to-sign formula is:
 * <pre>{@code METHOD\nFULL_URL\nSHA256(body)\ntimestamp\nnonce}</pre>
 *
 * <p>Key: 64-char hex decoded to 32 raw bytes before use.
 * Replay window: ±300 seconds.
 * Comparison: constant-time via {@link MessageDigest#isEqual}.
 */
@Service
public class HmacVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(HmacVerificationService.class);
    private static final int REPLAY_WINDOW_SEC = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private final McpContractProperties properties;

    public HmacVerificationService(McpContractProperties properties) {
        this.properties = properties;
    }

    /**
     * Result of an HMAC verification attempt.
     */
    public record Result(boolean ok, String error) {
        public static Result success() {
            return new Result(true, null);
        }

        public static Result fail(String error) {
            return new Result(false, error);
        }
    }

    /**
     * Verifies the HMAC signature on an incoming hub request.
     *
     * @param method     HTTP method (uppercase, always "POST")
     * @param fullUrl    complete request URL as received by the server
     * @param rawBody    raw request body bytes
     * @param timestamp  value of the {@code X-Hub-Timestamp} header (may be null)
     * @param nonce      value of the {@code X-Hub-Nonce} header (may be null)
     * @param sigHeader  value of the {@code X-Hub-Signature-256} header (may be null)
     * @return {@link Result#ok()} on success, or a failure result with an error message
     */
    public Result verify(String method, String fullUrl, byte[] rawBody, String timestamp, String nonce, String sigHeader) {
        String hmacKeyHex = properties.getHmacKey();
        if (hmacKeyHex == null || hmacKeyHex.isBlank()) {
            LOG.error("MCP_CONTRACT_HMAC_KEY is not configured — rejecting request");
            return Result.fail("HMAC key not configured");
        }

        if (timestamp == null || nonce == null || sigHeader == null) {
            return Result.fail("Missing HMAC headers");
        }

        // Parse and validate timestamp
        long tsNum;
        try {
            tsNum = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return Result.fail("Invalid timestamp format");
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSec - tsNum) > REPLAY_WINDOW_SEC) {
            return Result.fail("Timestamp out of tolerance window");
        }

        try {
            // SHA-256 hash of the raw body (lowercase hex)
            MessageDigest sha256 = MessageDigest.getInstance(SHA256_ALGORITHM);
            String bodyHash = HexFormat.of().formatHex(sha256.digest(rawBody != null ? rawBody : new byte[0]));

            // Construct the message to sign
            String message = method + "\n" + fullUrl + "\n" + bodyHash + "\n" + timestamp + "\n" + nonce;

            // Decode hex key to 32 raw bytes
            byte[] rawKey = HexFormat.of().parseHex(hmacKeyHex);

            // Compute HMAC-SHA256
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(rawKey, HMAC_ALGORITHM));
            String expectedSig = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            String expectedHeader = "sha256=" + expectedSig;

            // Constant-time comparison
            byte[] expectedBytes = expectedHeader.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = sigHeader.getBytes(StandardCharsets.UTF_8);

            if (expectedBytes.length != actualBytes.length || !MessageDigest.isEqual(expectedBytes, actualBytes)) {
                return Result.fail("Signature mismatch");
            }

            return Result.success();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOG.error("HMAC verification error", e);
            return Result.fail("HMAC computation failed");
        } catch (IllegalArgumentException e) {
            // HexFormat.parseHex throws if the key is not valid hex
            LOG.error("Invalid HMAC key format — must be 64 hex characters", e);
            return Result.fail("Invalid HMAC key format");
        }
    }
}
