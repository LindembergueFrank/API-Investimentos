package api_tech.api_investimentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

@ConfigurationProperties(prefix = "api.cors")
public record FrontendCorsProperties(List<String> allowedOrigins) {

    public FrontendCorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(FrontendCorsProperties::validateOrigin)
                .distinct()
                .toList();
    }

    private static void validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("api.cors.allowed-origins contains an invalid URI", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())
                || origin.contains("*")) {
            throw new IllegalArgumentException(
                    "api.cors.allowed-origins must contain exact HTTP(S) origins without path or wildcard");
        }
    }
}
