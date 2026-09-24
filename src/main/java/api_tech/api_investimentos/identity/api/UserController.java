package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.identity.application.CreateUserCommand;
import api_tech.api_investimentos.identity.application.UpdateUserCommand;
import api_tech.api_investimentos.identity.application.UserNotFoundException;
import api_tech.api_investimentos.identity.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
@Tag(name = "Users", description = "Cadastro e manutenção de usuários")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @Operation(summary = "Cria um usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody CreateUserDto createUserDto) {
        var command = new CreateUserCommand(
                createUserDto.username(),
                createUserDto.email(),
                createUserDto.password()
        );
        UUID userId = userService.createUser(command);
        var createdUser = userService.getUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        URI location = URI.create("/v1/users/" + userId);

        return ResponseEntity.created(location).body(UserResponseDto.from(createdUser));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um usuário pelo identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable("id") UUID id) {
        var user = userService.getUserById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        return ResponseEntity.ok(UserResponseDto.from(user));
    }

    @GetMapping
    @Operation(summary = "Lista usuários")
    @ApiResponse(responseCode = "200", description = "Usuários cadastrados",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = UserResponseDto.class))))
    public ResponseEntity<List<UserResponseDto>> listUsers() {
        var users = userService.listUsers().stream()
                .map(UserResponseDto::from)
                .toList();

        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza parcialmente um usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário atualizado"),
            @ApiResponse(responseCode = "400", description = "Requisição ou identificador inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<Void> updateUserById(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserDto updateUserDto
    ) {
        var command = new UpdateUserCommand(updateUserDto.username(), updateUserDto.password());
        userService.updateUserById(id, command);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário removido"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<Void> deleteById(@PathVariable("id") UUID id) {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
