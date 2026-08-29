package api_tech.api_investimentos.controller;

import api_tech.api_investimentos.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String username,
        String email,
        Instant creationTimestamp,
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
