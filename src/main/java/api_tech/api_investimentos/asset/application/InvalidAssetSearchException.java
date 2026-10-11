package api_tech.api_investimentos.asset.application;

public class InvalidAssetSearchException extends RuntimeException {
    public InvalidAssetSearchException() {
        super("Asset search query must contain at most 80 characters.");
    }
}
