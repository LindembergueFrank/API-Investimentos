package api_tech.api_investimentos.controller;

import api_tech.api_investimentos.entity.User;
import api_tech.api_investimentos.repository.UserRepository;
import api_tech.api_investimentos.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(("/v1/users"))
public class UserController {

    private final UserRepository userRepository;
    private UserService userService;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody CreateUserDto createUserDto) {
        //Criando usuario
        UUID userId = userService.createUser(createUserDto);
        // Retornando o Id no cabecalho
        var createdUser = userService.getUserById(userId.toString()).orElseThrow();
        URI location = URI.create("/v1/users/" + userId);
        return ResponseEntity.created(location).body(createdUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable ("id") String id){
        var user = userService.getUserById(id);

        //Logica para verificar o que a API vai retornar
        if (user.isPresent()){
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping()
    public ResponseEntity<List<User>> listUsers(){
        var users = userService.listUsers();

        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateUserById(@PathVariable ("id") String id, @RequestBody UpdateUserDto updateUserDto) {
        userService.updateUserById(id, updateUserDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping({"/{id}"})
    public ResponseEntity<Void> deleteById(@PathVariable ("id") String id){
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
