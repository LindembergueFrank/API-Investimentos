package api_tech.api_investimentos.controller;

import api_tech.api_investimentos.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserDto createUserDto) {
        UUID userId = userService.createUser(createUserDto);
        var createdUser = userService.getUserById(userId);
        URI location = URI.create("/v1/users/" + userId);

        return ResponseEntity.created(location).body(UserResponseDto.from(createdUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable("id") UUID id) {
        var user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponseDto.from(user));
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> listUsers() {
        var users = userService.listUsers().stream()
                .map(UserResponseDto::from)
                .toList();

        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateUserById(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserDto updateUserDto
    ) {
        userService.updateUserById(id, updateUserDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable("id") UUID id) {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
