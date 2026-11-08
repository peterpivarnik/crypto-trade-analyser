package com.psw.cta.processor;

import static com.psw.cta.dto.binance.SymbolStatus.BREAK;
import static com.psw.cta.dto.binance.SymbolStatus.TRADING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.SymbolStatus;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanBalanceCheckerTest {

    @Mock
    private BinanceService binanceService;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private OrphanBalanceChecker orphanBalanceChecker;

    @BeforeEach
    void setUp() {
        orphanBalanceChecker = new OrphanBalanceChecker(binanceService, logger);
        lenient().when(binanceService.getMinNotional(any())).thenReturn(new BigDecimal("0.0001"));
        lenient().when(exchangeInfo.getSymbols()).thenReturn(List.of(symbolInfo("ADABTC", TRADING),
                                                                     symbolInfo("FILBTC", TRADING),
                                                                     symbolInfo("HALTBTC", BREAK)));
    }

    @Test
    void shouldReportFreeBalanceWorthAtLeastMinimalOrderValue() {
        freeBalances(Map.of("ADA", "2521.5"));
        when(binanceService.getCurrentPrice("ADABTC")).thenReturn(new BigDecimal("0.00000254"));

        List<String> orphanBalances = orphanBalanceChecker.findOrphanBalances(exchangeInfo);

        assertThat(orphanBalances).containsExactly("ADA qty=2521.5 value=0.00640461 BTC");
    }

    @Test
    void shouldIgnoreBtcAndBnb() {
        lenient().when(exchangeInfo.getSymbols()).thenReturn(List.of(symbolInfo("BNBBTC", TRADING)));
        lenient().when(binanceService.getCurrentPrice(any())).thenReturn(BigDecimal.ONE);
        freeBalances(Map.of("BTC", "0.04", "BNB", "2.2"));

        assertThat(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).isEmpty();
    }

    @Test
    void shouldIgnoreDustBelowMinimalOrderValue() {
        freeBalances(Map.of("FIL", "0.5"));
        when(binanceService.getCurrentPrice("FILBTC")).thenReturn(new BigDecimal("0.00001066"));

        assertThat(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).isEmpty();
    }

    @Test
    void shouldIgnoreAssetsWithoutTradingBtcPair() {
        freeBalances(Map.of("AIRDROP", "1000", "HALT", "1000"));

        assertThat(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).isEmpty();
    }

    @Test
    void shouldIgnoreAssetsWithoutPrice() {
        freeBalances(Map.of("ADA", "2521.5"));
        when(binanceService.getCurrentPrice("ADABTC")).thenThrow(new CryptoTraderException("No price found!"));

        assertThat(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).isEmpty();
    }

    private void freeBalances(Map<String, String> balances) {
        Map<String, BigDecimal> freeBalances = new LinkedHashMap<>();
        balances.forEach((asset, free) -> freeBalances.put(asset, new BigDecimal(free)));
        when(binanceService.getMyFreeBalances()).thenReturn(freeBalances);
    }

    private SymbolInfo symbolInfo(String symbol, SymbolStatus status) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol(symbol);
        symbolInfo.setStatus(status);
        return symbolInfo;
    }
}
