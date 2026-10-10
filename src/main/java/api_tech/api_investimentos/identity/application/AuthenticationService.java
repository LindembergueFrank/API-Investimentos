package api_tech.api_investimentos.identity.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public AccessToken authenticate(AuthenticateUserCommand command) {
        var user = userRepository.findByEmail(command.email());
        String encodedPassword = user.map(candidate -> candidate.getPassword())
                .orElse(DUMMY_PASSWORD_HASH);

        if (!passwordEncoder.matches(command.password(), encodedPassword) || user.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        return accessTokenIssuer.issue(user.orElseThrow());
    }
}
