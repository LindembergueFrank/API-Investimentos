package api_tech.api_investimentos.identity.infrastructure;

import api_tech.api_investimentos.identity.application.UserRepository;
import api_tech.api_investimentos.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {
}
