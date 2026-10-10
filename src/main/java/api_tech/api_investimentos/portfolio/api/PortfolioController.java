package api_tech.api_investimentos.portfolio.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.portfolio.application.CreatePortfolioCommand;
import api_tech.api_investimentos.portfolio.application.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios")
@Tag(name = "Portfolios", description = "Carteiras pertencentes ao usuário autenticado")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    @Operation(summary = "Cria uma carteira para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Carteira criada",
                    content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<PortfolioResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePortfolioRequest request
    ) {
        var portfolio = portfolioService.create(new CreatePortfolioCommand(ownerId(jwt), request.name()));
        return ResponseEntity.created(URI.create("/v1/portfolios/" + portfolio.getId()))
                .body(PortfolioResponse.from(portfolio));
    }

    @GetMapping
    @Operation(summary = "Lista as carteiras do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de carteiras do usuário",
                    content = @Content(schema = @Schema(implementation = PortfolioPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Paginação inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<PortfolioPageResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PortfolioPageResponse.from(portfolioService.listByOwner(ownerId(jwt), page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma carteira pertencente ao usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carteira encontrada",
                    content = @Content(schema = @Schema(implementation = PortfolioResponse.class))),
            @ApiResponse(responseCode = "404", description = "Carteira inexistente ou pertencente a outro usuário",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<PortfolioResponse> getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(PortfolioResponse.from(portfolioService.getById(id, ownerId(jwt))));
    }

    private static UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
