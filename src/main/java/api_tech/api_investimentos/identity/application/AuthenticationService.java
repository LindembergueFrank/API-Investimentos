package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.config.RefreshTokenProperties;
import api_tech.api_investimentos.identity.domain.RefreshToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthenticatedSession refresh(String refreshTokenValue) {
        Instant now = Instant.now();
        var storedToken = refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.isRevoked()) {
            revokeFamily(storedToken.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (storedToken.isExpired(now)) {
            storedToken.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        var user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        storedToken.revoke(now);
        return issueSession(user.getId(), storedToken.getFamilyId());
    }

    @Transactional
    public void revoke(String refreshTokenValue) {
        refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(refreshTokenValue))
                .ifPresent(token -> revokeFamily(token.getFamilyId(), Instant.now()));
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

    private void revokeFamily(UUID familyId, Instant now) {
        refreshTokenRepository.findAllByFamilyId(familyId)
                .forEach(token -> token.revoke(now));
    }
}
