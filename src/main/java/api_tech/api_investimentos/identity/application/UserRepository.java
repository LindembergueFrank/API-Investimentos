package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.identity.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    <S extends User> S save(S user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    boolean existsById(UUID id);

    void deleteById(UUID id);
}
