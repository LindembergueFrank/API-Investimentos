package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.identity.domain.RefreshToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    <S extends RefreshToken> S save(S refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyId(UUID familyId);
}
