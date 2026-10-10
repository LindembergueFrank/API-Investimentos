package api_tech.api_investimentos.portfolio.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePortfolioRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 80, message = "must have at most 80 characters")
        String name
) {
}
