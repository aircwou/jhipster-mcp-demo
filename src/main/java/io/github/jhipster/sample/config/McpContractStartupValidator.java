package io.github.jhipster.sample.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;

/**
 * Fails application startup in non-development profiles when the MCPHub HMAC key is
 * absent or still set to the sample placeholder.
 *
 * <p>Contract requirement: "A tenant MUST refuse to start in a non-development profile
 * if {@code MCP_CONTRACT_HMAC_KEY} is absent or set to a placeholder value."
 *
 * <p>The {@code dev} and {@code test} profiles are exempt so local runs and the test
 * suite boot without a real secret.
 */
@Component
public class McpContractStartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(McpContractStartupValidator.class);

    /** Sample value shipped in application-secret-samples.yml — never valid in production. */
    private static final String PLACEHOLDER_HMAC_KEY = "7cbd0c3d76c593e154280fb6b77a43a4b2a6a8ac6e1e45481e4be2c6a6943094";

    private final Environment env;
    private final McpContractProperties properties;

    public McpContractStartupValidator(Environment env, McpContractProperties properties) {
        this.env = env;
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        boolean exempt = env.acceptsProfiles(
            Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST)
        );
        if (exempt) {
            return;
        }

        String key = properties.getHmacKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "MCP_CONTRACT_HMAC_KEY is not set. The application refuses to start in a non-development profile without a valid HMAC key."
            );
        }
        if (PLACEHOLDER_HMAC_KEY.equalsIgnoreCase(key)) {
            throw new IllegalStateException(
                "MCP_CONTRACT_HMAC_KEY is still the sample placeholder value. Set a real 64-character hex secret before starting in a non-development profile."
            );
        }
        if (!key.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalStateException("MCP_CONTRACT_HMAC_KEY must be exactly 64 hexadecimal characters (32 bytes).");
        }
        LOG.info("MCPHub contract HMAC key validated for non-development profile.");
    }
}
