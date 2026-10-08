package io.github.jhipster.sample.repository;

import io.github.jhipster.sample.domain.BankAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the BankAccount entity.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    @Query("select bankAccount from BankAccount bankAccount where bankAccount.user.login = ?#{authentication.name}")
    List<BankAccount> findByUserIsCurrentUser();

    /** Ids of the user's own bank accounts; no security context needed (MCP exchange/validate). */
    @Query("select bankAccount.id from BankAccount bankAccount where bankAccount.user.id = :userId order by bankAccount.id")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @Query("select bankAccount.id from BankAccount bankAccount order by bankAccount.id")
    List<Long> findAllIds();

    default Optional<BankAccount> findOneWithEagerRelationships(Long id) {
        return this.findOneWithToOneRelationships(id);
    }

    default List<BankAccount> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<BankAccount> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select bankAccount from BankAccount bankAccount left join fetch bankAccount.user",
        countQuery = "select count(bankAccount) from BankAccount bankAccount"
    )
    Page<BankAccount> findAllWithToOneRelationships(Pageable pageable);

    @Query("select bankAccount from BankAccount bankAccount left join fetch bankAccount.user")
    List<BankAccount> findAllWithToOneRelationships();

    @Query("select bankAccount from BankAccount bankAccount left join fetch bankAccount.user where bankAccount.id =:id")
    Optional<BankAccount> findOneWithToOneRelationships(@Param("id") Long id);
}
