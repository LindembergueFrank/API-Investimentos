package api_tech.api_investimentos.identity.application;

import api_tech.api_investimentos.identity.domain.User;
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

    public UUID createUser(CreateUserCommand command) {
        var entity = new User(
                UUID.randomUUID(),
                command.username(),
                command.email(),
                passwordEncoder.encode(command.password()),
                Instant.now(),
                null
        );

        var userSaved = userRepository.save(entity);
        return userSaved.getId();
    }

    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public void updateUserById(UUID id, UpdateUserCommand command) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (command.username() != null && !command.username().isBlank()) {
            user.setUsername(command.username());
        }

        if (command.password() != null && !command.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(command.password()));
        }

        userRepository.save(user);
    }

    public void deleteById(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }

        userRepository.deleteById(id);
    }
}
