package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.identity.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(
        @Schema(example = "9cc32af0-6bd3-47aa-9f03-2bf93825b5be")
        UUID id,

        @Schema(example = "investidor")
        String username,

        @Schema(example = "investidor@example.com")
        String email,

        @Schema(example = "2026-08-29T16:00:00Z")
        Instant creationTimestamp,

        @Schema(example = "2026-08-29T16:00:00Z")
        Instant updateTimestamp
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreationTimestamp(),
                user.getUpdateTimestamp()
        );
    }
}
