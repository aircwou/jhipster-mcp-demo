package io.github.jhipster.sample.config;

import io.github.jhipster.sample.security.mcp.HmacVerificationService;
import io.github.jhipster.sample.web.filter.McpHmacFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link McpHmacFilter} for all HMAC-signed MCPHub endpoints.
 */
@Configuration
public class McpFilterConfig {

    @Bean
    public FilterRegistrationBean<McpHmacFilter> mcpHmacFilterRegistration(HmacVerificationService hmacVerificationService) {
        var registration = new FilterRegistrationBean<>(new McpHmacFilter(hmacVerificationService));
        registration.addUrlPatterns(
            "/api/mcp/exchange",
            "/api/mcp/validate",
            "/api/mcp/refresh",
            "/api/mcp/logout",
            "/api/mcp/users"
            // "/api/mcp/roles" — intentionally NOT HMAC-protected (served unsigned)
        );
        registration.setOrder(1);
        registration.setName("mcpHmacFilter");
        return registration;
    }
}
