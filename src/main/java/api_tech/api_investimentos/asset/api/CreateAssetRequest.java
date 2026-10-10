package api_tech.api_investimentos.asset.api;

import api_tech.api_investimentos.asset.domain.AssetMarket;
import api_tech.api_investimentos.asset.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(
        @NotNull AssetMarket market,
        @NotBlank @Pattern(regexp = "(?i)[A-Z0-9]{4,12}", message = "must contain 4 to 12 letters or digits") String ticker,
        @NotNull AssetType type,
        @NotBlank @Size(max = 120) String name
) {
}
