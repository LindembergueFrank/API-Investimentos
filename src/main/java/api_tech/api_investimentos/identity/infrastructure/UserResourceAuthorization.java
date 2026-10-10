package api_tech.api_investimentos.identity.infrastructure;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserResourceAuthorization {

    public boolean isOwner(Authentication authentication, UUID userId) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        return userId.toString().equals(jwtAuthentication.getToken().getSubject());
    }
}
