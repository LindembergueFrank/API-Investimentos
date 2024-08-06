package api_tech.api_investimentos.service;

import api_tech.api_investimentos.controller.CreateUserDto;
import api_tech.api_investimentos.controller.UpdateUserDto;
import api_tech.api_investimentos.entity.User;
import api_tech.api_investimentos.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    // Arrange
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userArgumentCaptor;

    @Captor
    private ArgumentCaptor<UUID> uuidArgumentCaptor;

    @Nested
    class createUser {

        // Cenario positivo
        @Test
        @DisplayName("Deve criar usuario com sucesso")
        void shouldCreateUser() {

            // Arrange
            var user = new User(
                    UUID.randomUUID(),
                    "usernameteste",
                    "emailteste@replay.com",
                    "password",
                    Instant.now(),
                    null
            );
            doReturn(user).when(userRepository).save(userArgumentCaptor.capture());
            var input = new CreateUserDto("usernameteste",
                    "emailteste@replay.com",
                    "Senhateste001");
            // Act
            var output = userService.createUser(input);

            // Assert
            assertNotNull(output);
            var userCaptorValue = userArgumentCaptor.getValue();

            assertEquals(input.username(), userCaptorValue.getUsername());
            assertEquals(input.email(), userCaptorValue.getEmail());
            assertEquals(input.password(), userCaptorValue.getPassword());
        }

        // Cenario negativo
        @Test
        @DisplayName("Deve gerar exceção quando houver erro")
        void shouldThrowExceptionWhenErrorOccurs() {
            // Arrange
            doThrow(new RuntimeException()).when(userRepository).save(any());
            var input = new CreateUserDto("usernameteste",
                    "emailteste@replay.com",
                    "Senhateste001");

            // Act, Assert
            assertThrows(RuntimeException.class, () -> userService.createUser(input));
        }
    }

    @Nested
    class getUserById {

        // Cenario positivo
        @Test
        @DisplayName("Deve retornar usuário pelo id com sucesso quando houver optional")
        void shouldGetUserByIdWhenOptionalIsPresent() {

            // Arrange
            var user = new User(
                    UUID.randomUUID(),
                    "usernameteste",
                    "emailteste@replay.com",
                    "password",
                    Instant.now(),
                    null
            );
            doReturn(Optional.of(user))
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            // Act
            var output = userService.getUserById(user.getId().toString());

            // Assert
            assertTrue(output.isPresent());
            assertEquals(user.getId(), uuidArgumentCaptor.getValue());
        }

        @Test
        @DisplayName("Deve retornar usuário pelo id com sucesso quando nao houver optional")
        void shouldGetUserByIdWhenOptionalIsEmpty() {

            // Arrange
            var id = UUID.randomUUID();
            doReturn(Optional.empty())
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            // Act
            var output = userService.getUserById(id.toString());

            // Assert
            assertTrue(output.isEmpty());
            assertEquals(id, uuidArgumentCaptor.getValue());
        }
    }

    @Nested
    class listUsers {

        // Cenario positivo
        @Test
        @DisplayName("Deve retornar lista de usuarios com sucesso")
        void shouldReturnAllUsersWithSucess() {

            // Arrange
            var user = new User(
                    UUID.randomUUID(),
                    "usernameteste",
                    "emailteste@replay.com",
                    "password",
                    Instant.now(),
                    null
            );
            var userList = List.of(user);
            doReturn(userList)
                    .when(userRepository)
                    .findAll();

            // Act
            var output = userService.listUsers();

            // Assert
            assertNotNull(output);
            assertEquals(userList.size(), output.size());
        }


    }
    
    @Nested
    class updateUserById {

        // Cenario positivo
        @Test
        @DisplayName("Deve atualizar usuário pelo id quando existir e usuario e senha preeenchidas")
        void shouldUpdateUserByIdWhenUserAndPasswordExist() {

            // Arrange
            var updateUserDto = new UpdateUserDto(
                    "newusername",
                    "newpassword"
            );
            var user = new User(
                    UUID.randomUUID(),
                    "usernameteste",
                    "emailteste@replay.com",
                    "password",
                    Instant.now(),
                    null
            );

            doReturn(Optional.of(user))
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());
            doReturn(user)
                    .when(userRepository)
                    .save(userArgumentCaptor.capture());

            // Act
            userService.updateUserById(user.getId().toString(), updateUserDto);

            // Assert
            assertEquals(user.getId(), uuidArgumentCaptor.getValue());

            var userCaptured = userArgumentCaptor.getValue();

            assertEquals(updateUserDto.username(), userCaptured.getUsername());
            assertEquals(updateUserDto.password(), userCaptured.getPassword());

            verify(userRepository, times(1))
                    .findById(uuidArgumentCaptor.getValue());
            verify(userRepository, times(1))
                    .save(user);

        }

        // Cenario positivo
        @Test
        @DisplayName("Nao deve atualizar usuário pelo id quando nao existir e usuario e senha preeenchidas")
        void shouldNotUpdateUserByIdWhenUserNotAndPasswordExist() {

            // Arrange
            var updateUserDto = new UpdateUserDto(
                    "newusername",
                    "newpassword"
            );
            var id = UUID.randomUUID();

            doReturn(Optional.empty())
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            // Act
            userService.updateUserById(id.toString(), updateUserDto);

            // Assert
            assertEquals(id, uuidArgumentCaptor.getValue());

            verify(userRepository, times(1))
                    .findById(uuidArgumentCaptor.getValue());
            verify(userRepository, times(0))
                    .save(any());
        }
    }

    @Nested
    class deleteById {

        @Test
        @DisplayName("Deve deletar usuario por ID com sucesso quando usuario existir")
        void shouldDeleteUserIdWithSucessWhenUserExist() {

            // Arrange
            doReturn(true)
                    .when(userRepository)
                    .existsById(uuidArgumentCaptor.capture());

            doNothing()
                    .when(userRepository)
                    .deleteById(uuidArgumentCaptor.capture());

            var id = UUID.randomUUID();

            // Act
            userService.deleteById(id.toString());

            // Assert
            var idList = uuidArgumentCaptor.getAllValues();
            assertEquals(id, idList.get(0));
            assertEquals(id, idList.get(1));

            verify(userRepository, times(1)).existsById(idList.get(0));
            verify(userRepository, times(1)).deleteById(idList.get(1));
        }

        // Cenario negativo
        @Test
        @DisplayName("Nao deve deletar usuario por ID quando usuario  existir")
        void shouldNotDeleteUserIdWithSucessWhenUserNotExist() {

            // Arrange
            doReturn(false)
                    .when(userRepository)
                    .existsById(uuidArgumentCaptor.capture());

            var id = UUID.randomUUID();

            // Act
            userService.deleteById(id.toString());

            // Assert
            assertEquals(id, uuidArgumentCaptor.getValue());

            verify(userRepository, times(1))
                    .existsById(uuidArgumentCaptor.getValue());
            verify(userRepository, times(0))
                    .deleteById(any());
        }
    }
}