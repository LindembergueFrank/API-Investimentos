package api_tech.api_investimentos.asset.application;

import api_tech.api_investimentos.asset.domain.Asset;
import java.util.List;

public record AssetPage(List<Asset> items, int page, int size, long totalElements, int totalPages) {
}
