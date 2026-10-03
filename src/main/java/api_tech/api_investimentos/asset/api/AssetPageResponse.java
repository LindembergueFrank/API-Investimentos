package api_tech.api_investimentos.asset.api;

import api_tech.api_investimentos.asset.application.AssetPage;
import java.util.List;

public record AssetPageResponse(List<AssetResponse> items, int page, int size, long totalElements, int totalPages) {
    public static AssetPageResponse from(AssetPage page) {
        return new AssetPageResponse(page.items().stream().map(AssetResponse::from).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
