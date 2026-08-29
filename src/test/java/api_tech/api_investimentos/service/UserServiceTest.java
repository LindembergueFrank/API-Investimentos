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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userArgumentCaptor;

    @Captor
    private ArgumentCaptor<UUID> uuidArgumentCaptor;

    @Nested
    class CreateUser {

        @Test
        @DisplayName("Deve criar usuário armazenando a senha codificada")
        void shouldCreateUserWithEncodedPassword() {
            var persistedUser = new User(
                    UUID.randomUUID(),
                    "usernameteste",
                    "emailteste@replay.com",
                    "encoded-password",
                    Instant.now(),
                    null
            );
            var input = new CreateUserDto(
                    "usernameteste",
                    "emailteste@replay.com",
                    "Senhateste001"
            );

            when(passwordEncoder.encode(input.password())).thenReturn("encoded-password");
            doReturn(persistedUser).when(userRepository).save(userArgumentCaptor.capture());

            var output = userService.createUser(input);

            assertNotNull(output);
            var capturedUser = userArgumentCaptor.getValue();
            assertEquals(input.username(), capturedUser.getUsername());
            assertEquals(input.email(), capturedUser.getEmail());
            assertEquals("encoded-password", capturedUser.getPassword());
            assertNotEquals(input.password(), capturedUser.getPassword());
            verify(passwordEncoder).encode(input.password());
        }

        @Test
        @DisplayName("Deve propagar exceção quando a persistência falhar")
        void shouldThrowExceptionWhenErrorOccurs() {
            var input = new CreateUserDto(
                    "usernameteste",
                    "emailteste@replay.com",
                    "Senhateste001"
            );

            when(passwordEncoder.encode(input.password())).thenReturn("encoded-password");
            doThrow(new RuntimeException()).when(userRepository).save(any());

            assertThrows(RuntimeException.class, () -> userService.createUser(input));
        }
    }

    @Nested
    class GetUserById {

        @Test
        @DisplayName("Deve retornar usuário quando o id existir")
        void shouldGetUserByIdWhenOptionalIsPresent() {
            var user = sampleUser();
            doReturn(Optional.of(user))
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            var output = userService.getUserById(user.getId().toString());

            assertTrue(output.isPresent());
            assertEquals(user.getId(), uuidArgumentCaptor.getValue());
        }

        @Test
        @DisplayName("Deve retornar vazio quando o id não existir")
        void shouldGetUserByIdWhenOptionalIsEmpty() {
            var id = UUID.randomUUID();
            doReturn(Optional.empty())
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            var output = userService.getUserById(id.toString());

            assertTrue(output.isEmpty());
            assertEquals(id, uuidArgumentCaptor.getValue());
        }
    }

    @Nested
    class ListUsers {

        @Test
        @DisplayName("Deve retornar todos os usuários")
        void shouldReturnAllUsers() {
            var userList = List.of(sampleUser());
            doReturn(userList).when(userRepository).findAll();

            var output = userService.listUsers();

            assertNotNull(output);
            assertEquals(userList.size(), output.size());
        }
    }

    @Nested
    class UpdateUserById {

        @Test
        @DisplayName("Deve atualizar usuário e armazenar nova senha codificada")
        void shouldUpdateUserByIdWhenUserAndPasswordExist() {
            var updateUserDto = new UpdateUserDto("newusername", "newpassword");
            var user = sampleUser();

            doReturn(Optional.of(user))
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());
            when(passwordEncoder.encode(updateUserDto.password())).thenReturn("encoded-newpassword");
            doReturn(user).when(userRepository).save(userArgumentCaptor.capture());

            userService.updateUserById(user.getId().toString(), updateUserDto);

            assertEquals(user.getId(), uuidArgumentCaptor.getValue());
            var capturedUser = userArgumentCaptor.getValue();
            assertEquals(updateUserDto.username(), capturedUser.getUsername());
            assertEquals("encoded-newpassword", capturedUser.getPassword());
            assertNotEquals(updateUserDto.password(), capturedUser.getPassword());
            verify(passwordEncoder).encode(updateUserDto.password());
            verify(userRepository, times(1)).save(user);
        }

        @Test
        @DisplayName("Não deve atualizar quando o usuário não existir")
        void shouldNotUpdateUserByIdWhenUserDoesNotExist() {
            var updateUserDto = new UpdateUserDto("newusername", "newpassword");
            var id = UUID.randomUUID();

            doReturn(Optional.empty())
                    .when(userRepository)
                    .findById(uuidArgumentCaptor.capture());

            userService.updateUserById(id.toString(), updateUserDto);

            assertEquals(id, uuidArgumentCaptor.getValue());
            verify(userRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    class DeleteById {

        @Test
        @DisplayName("Deve deletar usuário quando ele existir")
        void shouldDeleteUserWhenUserExists() {
            doReturn(true)
                    .when(userRepository)
                    .existsById(uuidArgumentCaptor.capture());
            doNothing()
                    .when(userRepository)
                    .deleteById(uuidArgumentCaptor.capture());
            var id = UUID.randomUUID();

            userService.deleteById(id.toString());

            var idList = uuidArgumentCaptor.getAllValues();
            assertEquals(id, idList.get(0));
            assertEquals(id, idList.get(1));
            verify(userRepository, times(1)).existsById(idList.get(0));
            verify(userRepository, times(1)).deleteById(idList.get(1));
        }

        @Test
        @DisplayName("Não deve deletar usuário inexistente")
        void shouldNotDeleteUserWhenUserDoesNotExist() {
            doReturn(false)
                    .when(userRepository)
                    .existsById(uuidArgumentCaptor.capture());
            var id = UUID.randomUUID();

            userService.deleteById(id.toString());

            assertEquals(id, uuidArgumentCaptor.getValue());
            verify(userRepository, never()).deleteById(any());
        }
    }

    private User sampleUser() {
        return new User(
                UUID.randomUUID(),
                "usernameteste",
                "emailteste@replay.com",
                "encoded-password",
                Instant.now(),
                null
        );
    }
}
