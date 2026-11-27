package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.config.RefreshTokenProperties;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import api_tech.api_investimentos.identity.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec refreshTokenCodec;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec refreshTokenCodec,
            RefreshTokenProperties refreshTokenProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenCodec = refreshTokenCodec;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @Transactional
    public AuthenticatedSession authenticate(AuthenticateUserCommand command) {
        var user = userRepository.findByEmail(command.email());
        String encodedPassword = user.map(candidate -> candidate.getPassword())
                .orElse(DUMMY_PASSWORD_HASH);

        if (!passwordEncoder.matches(command.password(), encodedPassword) || user.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        var authenticatedUser = user.orElseThrow();
        return issueSession(authenticatedUser.getId(), UUID.randomUUID());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = InvalidRefreshTokenException.class)
    public AuthenticatedSession refresh(String refreshTokenValue) {
        String tokenHash = refreshTokenCodec.hash(refreshTokenValue);
        var user = lockSessionOwner(tokenHash).orElseThrow(InvalidRefreshTokenException::new);
        UUID familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);
        var familyTokens = refreshTokenRepository.findAllByFamilyIdForUpdate(familyId);
        var storedToken = familyTokens.stream()
                .filter(token -> token.getTokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (storedToken.isRevoked()) {
            revokeFamily(familyTokens, now);
            throw new InvalidRefreshTokenException();
        }

        if (storedToken.isExpired(now)) {
            storedToken.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        storedToken.revoke(now);
        return issueSession(user.getId(), familyId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(String refreshTokenValue) {
        String tokenHash = refreshTokenCodec.hash(refreshTokenValue);
        if (lockSessionOwner(tokenHash).isEmpty()) {
            return;
        }
        var familyId = refreshTokenRepository.findFamilyIdByTokenHash(tokenHash);
        if (familyId.isEmpty()) {
            return;
        }

        var familyTokens = refreshTokenRepository.findAllByFamilyIdForUpdate(familyId.get());
        if (familyTokens.stream().noneMatch(token -> token.getTokenHash().equals(tokenHash))) {
            return;
        }
        revokeFamily(familyTokens, Instant.now());
    }

    private Optional<User> lockSessionOwner(String tokenHash) {
        return refreshTokenRepository.findUserIdByTokenHash(tokenHash)
                .flatMap(userRepository::findByIdForUpdate);
    }

    private AuthenticatedSession issueSession(UUID userId, UUID familyId) {
        var user = userRepository.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenProperties.ttl());
        var generatedToken = refreshTokenCodec.generate();
        var storedToken = new RefreshToken(
                UUID.randomUUID(),
                userId,
                familyId,
                generatedToken.hash(),
                expiresAt,
                now,
                null
        );
        refreshTokenRepository.save(storedToken);

        return new AuthenticatedSession(
                accessTokenIssuer.issue(user),
                generatedToken.value(),
                refreshTokenProperties.ttl().toSeconds()
        );
    }

    private void revokeFamily(List<RefreshToken> familyTokens, Instant now) {
        familyTokens.forEach(token -> token.revoke(now));
    }
}
