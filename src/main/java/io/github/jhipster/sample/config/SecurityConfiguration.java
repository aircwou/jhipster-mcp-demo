package io.github.jhipster.sample.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.github.jhipster.sample.security.*;
import io.github.jhipster.sample.service.McpSessionService;
import io.github.jhipster.sample.web.filter.McpSessionAuthFilter;
import io.github.jhipster.sample.web.filter.SpaWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final Environment env;

    private final JHipsterProperties jHipsterProperties;

    private final McpSessionService mcpSessionService;

    public SecurityConfiguration(Environment env, JHipsterProperties jHipsterProperties, McpSessionService mcpSessionService) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
        this.mcpSessionService = mcpSessionService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(new McpSessionAuthFilter(mcpSessionService), BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(jHipsterProperties.getSecurity().getContentSecurityPolicy()))
                    .frameOptions(FrameOptionsConfig::sameOrigin)
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers("/index.html", "/*.js", "/*.txt", "/*.json", "/*.map", "/*.css").permitAll()
                    .requestMatchers("/*.ico", "/*.png", "/*.svg", "/*.webapp").permitAll()
                    .requestMatchers("/app/**").permitAll()
                    .requestMatchers("/i18n/**").permitAll()
                    .requestMatchers("/content/**").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/mcp-login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mcp/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mcp/exchange").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mcp/validate").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mcp/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/mcp/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/mcp/users").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/mcp/roles").permitAll()
                    .requestMatchers("/.well-known/mcp-hub-verification.json").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/authenticate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/authenticate").permitAll()
                    .requestMatchers("/api/register").permitAll()
                    .requestMatchers("/api/activate").permitAll()
                    .requestMatchers("/api/account/reset-password/init").permitAll()
                    .requestMatchers("/api/account/reset-password/finish").permitAll()
                    .requestMatchers("/api/admin/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers("/api/**").authenticated()
                    .requestMatchers("/v3/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers("/management/health").permitAll()
                    .requestMatchers("/management/health/**").permitAll()
                    .requestMatchers("/management/info").permitAll()
                    .requestMatchers("/management/prometheus").permitAll()
                    .requestMatchers("/management/**").hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> {
                // Skip JWT validation for mcp: prefixed session tokens.
                // We must short-circuit on the raw header BEFORE delegating to
                // DefaultBearerTokenResolver, because its internal regex
                // ("^Bearer (?<token>[a-zA-Z0-9-._~+/]+=*)$") rejects the ':' in
                // "mcp:<hex>" and throws OAuth2AuthenticationException, which
                // BearerTokenAuthenticationFilter then turns into a 401 — even
                // after McpSessionAuthFilter has already authenticated the user.
                var resolver = new DefaultBearerTokenResolver();
                oauth2.bearerTokenResolver(request -> {
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer mcp:", 0, 11)) {
                        return null;
                    }
                    return resolver.resolve(request);
                });
                oauth2.jwt(withDefaults());
            });
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            http.authorizeHttpRequests(authz -> authz.requestMatchers("/h2-console/**").permitAll());
        }
        return http.build();
    }
}
