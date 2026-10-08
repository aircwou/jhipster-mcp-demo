package io.github.jhipster.sample.service;

import io.github.jhipster.sample.domain.User;
import io.github.jhipster.sample.repository.BankAccountRepository;
import io.github.jhipster.sample.repository.UserRepository;
import io.github.jhipster.sample.security.AuthoritiesConstants;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code enum_scopes} payload LockMCP stores on a Tenant Delegated session: which bank
 * accounts the user may name in a tool call ({@code x-mcp-inject: "bankAccountIds"}).
 *
 * <p>Returned from {@code /api/mcp/exchange} (stored at login, so the first tool listing is
 * already scoped) and {@code /api/mcp/validate} (replaces it on every session check, so a change
 * of ownership reaches LockMCP within its 30 s validate cache).
 *
 * <p>Role names are JHipster's own ({@code ROLE_USER}): LockMCP matches them to its roles through
 * the connection's role mapping. One entry per authority the user holds, each with the same ids:
 * the user's own accounts, or every account for {@code ROLE_ADMIN}. An empty role list would NOT
 * mean "unrestricted" in LockMCP -- a scoped role with no ids is refused every value -- so admins
 * get all ids instead.
 */
@Service
@Transactional(readOnly = true)
public class McpEnumScopesService {

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;

    public McpEnumScopesService(UserRepository userRepository, BankAccountRepository bankAccountRepository) {
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    /** The payload for {@code userId}, or {@code null} when the user does not exist. */
    public Map<String, Object> forUser(Long userId) {
        // Authorities load lazily inside this read-only transaction, as in McpSessionService.getUserRoles.
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        List<String> authorities = user.getAuthorities().stream().map(a -> a.getName()).sorted().toList();
        List<Long> bankAccountIds = authorities.contains(AuthoritiesConstants.ADMIN)
            ? bankAccountRepository.findAllIds()
            : bankAccountRepository.findIdsByUserId(userId);
        List<Map<String, Object>> roles = authorities
            .stream()
            .map(name -> Map.<String, Object>of("role", name, "bankAccountIds", bankAccountIds))
            .toList();
        return Map.of(
            "email",
            user.getEmail() != null ? user.getEmail() : "",
            "accounts",
            List.of(Map.of("accountId", user.getId(), "roles", roles))
        );
    }
}
