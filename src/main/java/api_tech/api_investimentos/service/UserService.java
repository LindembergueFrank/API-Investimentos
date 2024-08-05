package api_tech.api_investimentos.service;

import api_tech.api_investimentos.controller.CreateUserDto;
import api_tech.api_investimentos.controller.UpdateUserDto;
import api_tech.api_investimentos.entity.User;
import api_tech.api_investimentos.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID createUser(CreateUserDto createUserDto){

        // DTO -> Entity
        var entity = new User(
                UUID.randomUUID(),
                createUserDto.username(),
                createUserDto.email(),
                createUserDto.password(),
                Instant.now(),
                null);
        var userSaved = userRepository.save(entity);

        // Retorna somente identificador
        return userSaved.getId();
    }

    // Usuario pode ou não existir
    public Optional<User> getUserById(String id){
        return userRepository.findById(UUID.fromString(id));
    }

    public List<User> listUsers(){
        return userRepository.findAll();
    }

    public void updateUserById(String id, UpdateUserDto updateUserDto){
        var userId = UUID.fromString(id);

        var userExists = userRepository.findById(userId);

        if(userExists.isPresent()){
            var user = userExists.get();

            // Atualizacao de nome de usuario e senha
            if(updateUserDto.username() != null) {
                user.setUsername(updateUserDto.username());
            }

            if(updateUserDto.username() != null) {
                user.setPassword(updateUserDto.password());
            }

            userRepository.save(user);
        }
    }

    public void deleteById(String id){
        var userId = UUID.fromString(id);

        var userExists = userRepository.existsById(userId);

        if(userExists){
            userRepository.deleteById(userId);
        }
    }
}
