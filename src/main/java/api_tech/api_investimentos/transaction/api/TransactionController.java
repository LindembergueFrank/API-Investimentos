package api_tech.api_investimentos.transaction.api;

import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import api_tech.api_investimentos.transaction.application.CreateTransactionCommand;
import api_tech.api_investimentos.transaction.application.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Operações imutáveis das carteiras")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Registra compra ou venda de ativo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operação criada", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Repetição idempotente da mesma operação", content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Carteira ou ativo não encontrado", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "409", description = "Saldo insuficiente ou chave idempotente conflitante", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CreateTransactionRequest request) {
        var result = service.create(new CreateTransactionCommand(request.requestId(), UUID.fromString(jwt.getSubject()),
                request.portfolioId(), request.assetId(), request.type(), request.quantity(), request.unitPrice(),
                request.fees(), request.occurredAt()));
        var body = TransactionResponse.from(result.transaction());
        return result.created()
                ? ResponseEntity.created(URI.create("/v1/transactions/" + body.id())).body(body)
                : ResponseEntity.ok(body);
    }
}
