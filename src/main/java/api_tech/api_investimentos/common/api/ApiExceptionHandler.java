package api_tech.api_investimentos.common.api;

import api_tech.api_investimentos.identity.application.InvalidCredentialsException;
import api_tech.api_investimentos.identity.application.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Comparator;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final URI VALIDATION_ERROR = problemType("validation-error");
    private static final URI INVALID_PARAMETER = problemType("invalid-parameter");
    private static final URI MALFORMED_REQUEST = problemType("malformed-request");
    private static final URI RESOURCE_NOT_FOUND = problemType("resource-not-found");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            WebRequest request
    ) {
        var violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();

        var problem = problem(
                HttpStatus.BAD_REQUEST,
                VALIDATION_ERROR,
                "Request validation failed",
                "One or more request fields are invalid.",
                request
        );
        problem.setProperty("errors", violations);

        return response(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            WebRequest request
    ) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                INVALID_PARAMETER,
                "Invalid request parameter",
                "The parameter '%s' has an invalid format.".formatted(exception.getName()),
                request
        );

        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            WebRequest request
    ) {
        var problem = problem(
                HttpStatus.BAD_REQUEST,
                MALFORMED_REQUEST,
                "Malformed request",
                "The request body could not be read.",
                request
        );

        return response(problem);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(
            UserNotFoundException exception,
            WebRequest request
    ) {
        var problem = problem(
                HttpStatus.NOT_FOUND,
                RESOURCE_NOT_FOUND,
                "Resource not found",
                exception.getMessage(),
                request
        );

        return response(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException exception,
            WebRequest request
    ) {
        var problem = problem(
                HttpStatus.UNAUTHORIZED,
                problemType("invalid-credentials"),
                "Invalid credentials",
                exception.getMessage(),
                request
        );

        return response(problem);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            WebRequest request
    ) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);

        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }

        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static URI problemType(String slug) {
        return URI.create("urn:problem-type:api-investimentos:" + slug);
    }
}
