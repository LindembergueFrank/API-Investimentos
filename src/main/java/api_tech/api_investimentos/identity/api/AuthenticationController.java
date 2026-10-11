package api_tech.api_investimentos.identity.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.identity.application.AuthenticateUserCommand;
import api_tech.api_investimentos.identity.application.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Emissão de credenciais de acesso")
public class AuthenticationController {

    static final String REFRESH_COOKIE = "__Host-api-investimentos-refresh";

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/token")
    @SecurityRequirements
    @Operation(summary = "Autentica um usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AccessTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<AccessTokenResponse> authenticate(@Valid @RequestBody LoginRequest request) {
        var session = authenticationService.authenticate(new AuthenticateUserCommand(request.email(), request.password()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(AccessTokenResponse.from(session));
    }

    @PostMapping("/browser/token")
    @SecurityRequirements
    @Operation(summary = "Autentica um navegador com refresh token protegido por cookie")
    public ResponseEntity<BrowserSessionResponse> authenticateBrowser(@Valid @RequestBody LoginRequest request) {
        var session = authenticationService.authenticate(new AuthenticateUserCommand(request.email(), request.password()));
        return browserSession(session);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renova uma sessão e rotaciona o refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão renovada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AccessTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado ou revogado",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<AccessTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        var session = authenticationService.refresh(request.refreshToken());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(AccessTokenResponse.from(session));
    }

    @PostMapping("/browser/refresh")
    @SecurityRequirements
    @Operation(summary = "Renova uma sessão de navegador e rotaciona o cookie de refresh")
    public ResponseEntity<BrowserSessionResponse> refreshBrowser(
            @CookieValue(name = REFRESH_COOKIE) String refreshToken
    ) {
        return browserSession(authenticationService.refresh(refreshToken));
    }

    @PostMapping("/revoke")
    @SecurityRequirements
    @Operation(summary = "Revoga uma família de refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Revogação processada"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "429", description = "Limite de requisições excedido",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<Void> revoke(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.revoke(request.refreshToken());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .build();
    }

    @PostMapping("/browser/revoke")
    @SecurityRequirements
    @Operation(summary = "Revoga e remove a sessão persistente do navegador")
    public ResponseEntity<Void> revokeBrowser(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authenticationService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<BrowserSessionResponse> browserSession(
            api_tech.api_investimentos.identity.application.AuthenticatedSession session
    ) {
        var refreshCookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(session.refreshTokenExpiresInSeconds())
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(BrowserSessionResponse.from(session));
    }

    private static ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
