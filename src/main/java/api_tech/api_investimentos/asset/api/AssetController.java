package api_tech.api_investimentos.asset.api;

import api_tech.api_investimentos.asset.application.AssetService;
import api_tech.api_investimentos.asset.application.CreateAssetCommand;
import api_tech.api_investimentos.common.api.ProblemDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/v1/assets")
@Tag(name = "Assets", description = "Catálogo controlado de ativos negociáveis")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra um ativo no catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ativo criado", content = @Content(schema = @Schema(implementation = AssetResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel administrativo obrigatório", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mercado e ticker já cadastrados", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetailResponse.class)))
    })
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody CreateAssetRequest request) {
        var asset = service.create(new CreateAssetCommand(request.market(), request.ticker(), request.type(), request.name()));
        return ResponseEntity.created(URI.create("/v1/assets/" + asset.getId())).body(AssetResponse.from(asset));
    }

    @GetMapping
    @Operation(summary = "Lista o catálogo de ativos")
    public ResponseEntity<AssetPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(AssetPageResponse.from(service.list(page, size)));
    }
}
