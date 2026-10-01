package io.github.jhipster.sample.repository;

import io.github.jhipster.sample.domain.McpSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link McpSession}.
 */
@Repository
public interface McpSessionRepository extends JpaRepository<McpSession, String> {
    Optional<McpSession> findByToken(String token);

    void deleteByToken(String token);

    void deleteByExpiresAtBefore(Instant cutoff);
}
