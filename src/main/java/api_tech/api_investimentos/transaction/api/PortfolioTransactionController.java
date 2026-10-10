package api_tech.api_investimentos.transaction.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.transaction.application.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios/{portfolioId}/transactions")
@Tag(name = "Transactions", description = "Operações imutáveis das carteiras")
public class PortfolioTransactionController {
    private final TransactionService service;

    public PortfolioTransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as operações de uma carteira do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de operações",
                    content = @Content(schema = @Schema(implementation = TransactionPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Paginação inválida",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Carteira inexistente ou pertencente a outro usuário",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<TransactionPageResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(TransactionPageResponse.from(
                service.listByPortfolio(portfolioId, UUID.fromString(jwt.getSubject()), page, size)));
    }
}
