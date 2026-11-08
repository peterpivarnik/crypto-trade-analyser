package com.psw.cta;

import static com.psw.cta.ServiceHandler.getRequiredVariable;
import static com.psw.cta.ServiceHandler.splitForbiddenPairs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psw.cta.exception.CryptoTraderException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceHandlerTest {

    @Test
    void shouldReturnEmptyListWhenForbiddenPairsAreMissing() {
        assertThat(splitForbiddenPairs(null)).isEmpty();
    }

    @Test
    void shouldTrimAndUppercaseForbiddenPairs() {
        assertThat(splitForbiddenPairs(" abcbtc, XYZBTC ,QwEbtc")).containsExactly("ABCBTC", "XYZBTC", "QWEBTC");
    }

    @Test
    void shouldIgnoreEmptyForbiddenPairs() {
        assertThat(splitForbiddenPairs("ABCBTC,, ,")).containsExactly("ABCBTC");
        assertThat(splitForbiddenPairs("")).isEmpty();
    }

    @Test
    void shouldReturnRequiredVariable() {
        assertThat(getRequiredVariable(Map.of("apiKey", "key"), "apiKey")).isEqualTo("key");
    }

    @Test
    void shouldFailWhenRequiredVariableIsMissingOrBlank() {
        assertThatThrownBy(() -> getRequiredVariable(Map.of(), "apiKey"))
            .isInstanceOf(CryptoTraderException.class)
            .hasMessage("Missing environment variable apiKey");
        assertThatThrownBy(() -> getRequiredVariable(Map.of("apiSecret", " "), "apiSecret"))
            .isInstanceOf(CryptoTraderException.class)
            .hasMessage("Missing environment variable apiSecret");
    }
}
