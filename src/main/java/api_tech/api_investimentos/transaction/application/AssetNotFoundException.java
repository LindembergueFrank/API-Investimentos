package api_tech.api_investimentos.transaction.application;

public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException() { super("Asset not found."); }
}
