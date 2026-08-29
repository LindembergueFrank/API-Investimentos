package api_tech.api_investimentos.service;

import api_tech.api_investimentos.controller.CreateUserDto;
import api_tech.api_investimentos.controller.UpdateUserDto;
import api_tech.api_investimentos.entity.User;
import api_tech.api_investimentos.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UUID createUser(CreateUserDto createUserDto) {
        var entity = new User(
                UUID.randomUUID(),
                createUserDto.username(),
                createUserDto.email(),
                passwordEncoder.encode(createUserDto.password()),
                Instant.now(),
                null
        );

        var userSaved = userRepository.save(entity);
        return userSaved.getId();
    }

    public Optional<User> getUserById(String id) {
        return userRepository.findById(UUID.fromString(id));
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public void updateUserById(String id, UpdateUserDto updateUserDto) {
        var userId = UUID.fromString(id);
        var userExists = userRepository.findById(userId);

        if (userExists.isEmpty()) {
            return;
        }

        var user = userExists.get();

        if (updateUserDto.username() != null && !updateUserDto.username().isBlank()) {
            user.setUsername(updateUserDto.username());
        }

        if (updateUserDto.password() != null && !updateUserDto.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(updateUserDto.password()));
        }

        userRepository.save(user);
    }

    public void deleteById(String id) {
        var userId = UUID.fromString(id);

        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        }
    }
}
