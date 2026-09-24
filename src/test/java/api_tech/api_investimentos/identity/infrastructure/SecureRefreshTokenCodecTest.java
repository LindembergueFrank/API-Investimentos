package api_tech.api_investimentos.identity.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureRefreshTokenCodecTest {

    private final SecureRefreshTokenCodec codec = new SecureRefreshTokenCodec();

    @Test
    void shouldGenerateRandomUrlSafeTokensAndDeterministicHashes() {
        var first = codec.generate();
        var second = codec.generate();

        assertNotEquals(first.value(), second.value());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(first.value().matches("[A-Za-z0-9_-]{43}"));
        assertTrue(first.hash().matches("[0-9a-f]{64}"));
        assertEquals(first.hash(), codec.hash(first.value()));
    }
}
