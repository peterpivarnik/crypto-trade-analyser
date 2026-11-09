package com.psw.cta.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.binance.DelistResponse;
import com.psw.cta.service.ForbiddenPairsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForbiddenPairsServiceTest {

    @Mock
    private BinanceAdapter binanceAdapter;
    @Mock
    private LambdaLogger logger;

    @Test
    void shouldCombineDefaultConfiguredAndDelistedPairs() {
        when(binanceAdapter.getDelistSchedule()).thenReturn(List.of(delistResponse(List.of("OLDBTC", "OLDUSDT"))));

        List<String> forbiddenPairs = getAllForbiddenPairs("ABCBTC");

        assertThat(forbiddenPairs).containsExactly("BNBBTC", "WBTCBTC", "PAXGBTC", "XAUTBTC", "ABCBTC", "OLDBTC");
    }

    @Test
    void shouldIgnoreDelistResponseWithoutSymbols() {
        when(binanceAdapter.getDelistSchedule()).thenReturn(List.of(delistResponse(null),
                                                                    delistResponse(List.of("OLDBTC"))));

        List<String> forbiddenPairs = getAllForbiddenPairs();

        assertThat(forbiddenPairs).contains("OLDBTC");
    }

    @Test
    void shouldNotContainDuplicates() {
        when(binanceAdapter.getDelistSchedule()).thenReturn(List.of(delistResponse(List.of("ABCBTC")),
                                                                    delistResponse(List.of("ABCBTC"))));

        List<String> forbiddenPairs = getAllForbiddenPairs("BNBBTC", "ABCBTC");

        assertThat(forbiddenPairs).containsExactly("BNBBTC", "WBTCBTC", "PAXGBTC", "XAUTBTC", "ABCBTC");
    }

    private List<String> getAllForbiddenPairs(String... configuredForbiddenPairs) {
        return new ForbiddenPairsService(binanceAdapter, List.of(configuredForbiddenPairs), logger)
            .getAllForbiddenPairs();
    }

    private DelistResponse delistResponse(List<String> symbols) {
        DelistResponse delistResponse = new DelistResponse();
        delistResponse.setSymbols(symbols);
        return delistResponse;
    }
}
