package api_tech.api_investimentos.position.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.position.application.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios/{portfolioId}/positions")
@Tag(name = "Positions", description = "Posições derivadas das operações da carteira")
public class PositionController {
    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as posições atuais da carteira do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Posições atuais",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PositionResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Carteira inexistente ou pertencente a outro usuário",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<List<PositionResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId
    ) {
        var positions = service.listByPortfolio(portfolioId, UUID.fromString(jwt.getSubject())).stream()
                .map(PositionResponse::from)
                .toList();
        return ResponseEntity.ok(positions);
    }
}
