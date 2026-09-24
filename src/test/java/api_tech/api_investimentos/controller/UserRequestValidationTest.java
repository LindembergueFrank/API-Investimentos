package api_tech.api_investimentos.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRequestValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidCreatePayload() {
        var request = new CreateUserDto("lindembergue", "dev@example.com", "strong-password");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectInvalidCreatePayload() {
        var request = new CreateUserDto("ab", "invalid-email", "short");

        var violations = validator.validate(request);

        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("username")));
        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("password")));
    }

    @Test
    void shouldAcceptPartialUpdatePayload() {
        var usernameOnly = new UpdateUserDto("newusername", null);
        var passwordOnly = new UpdateUserDto(null, "new-password");

        assertTrue(validator.validate(usernameOnly).isEmpty());
        assertTrue(validator.validate(passwordOnly).isEmpty());
    }

    @Test
    void shouldRejectInvalidProvidedUpdateField() {
        var request = new UpdateUserDto(null, "short");

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }
}
