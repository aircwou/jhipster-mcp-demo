package io.github.jhipster.sample.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persisted session token issued to MCPHub on behalf of an authenticated user.
 *
 * <p>Token format: {@code mcp:} + 64 lowercase hex characters (68 chars total).
 */
@Entity
@Table(name = "mcp_session")
public class McpSession {

    /** Primary key — the opaque session token ({@code mcp:<64-hex>}). */
    @Id
    @Column(name = "token", length = 68, nullable = false)
    private String token;

    /** FK to {@code jhi_user.id}. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Absolute expiry timestamp. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public McpSession() {}

    public McpSession(String token, Long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
