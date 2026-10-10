package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.identity.application.ExpiredSessionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class JpaExpiredSessionRepository implements ExpiredSessionRepository {

    private final EntityManager entityManager;

    public JpaExpiredSessionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public int deleteExpiredFamilies(Instant cutoff, int batchSize) {
        var familyIds = entityManager.createQuery("""
                        select token.familyId
                        from RefreshToken token
                        group by token.familyId
                        having max(token.expiresAt) <= :cutoff
                        order by max(token.expiresAt)
                        """, UUID.class)
                .setParameter("cutoff", cutoff)
                .setMaxResults(batchSize)
                .getResultList();

        if (familyIds.isEmpty()) {
            return 0;
        }

        return entityManager.createQuery("""
                        delete from RefreshToken token
                        where token.familyId in :familyIds
                        """)
                .setParameter("familyIds", familyIds)
                .executeUpdate();
    }
}
