package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.identity.domain.User;

public interface AccessTokenIssuer {

    AccessToken issue(User user);
}
