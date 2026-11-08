package com.psw.cta;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.processor.OrphanBalanceChecker;
import com.psw.cta.processor.trade.BnbTradeProcessor;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoTraderTest {

    private static final String ORPHAN = "ADA qty=2521.5 value=0.0064 BTC";

    @Mock
    private BinanceService binanceService;
    @Mock
    private BnbTradeProcessor bnbTradeProcessor;
    @Mock
    private LambdaTradeProcessor tradeProcessor;
    @Mock
    private OrphanBalanceChecker orphanBalanceChecker;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private CryptoTrader cryptoTrader;

    @BeforeEach
    void setUp() {
        cryptoTrader = new CryptoTrader(binanceService,
                                        bnbTradeProcessor,
                                        tradeProcessor,
                                        orphanBalanceChecker,
                                        logger);
        lenient().when(binanceService.getOpenOrders()).thenReturn(List.of());
        lenient().when(binanceService.getExchangeInfo()).thenReturn(exchangeInfo);
        lenient().when(binanceService.getCurrentPrice(any())).thenReturn(new BigDecimal("0.009"));
        lenient().when(bnbTradeProcessor.buyBnB(any(), any())).thenReturn(new BigDecimal("2"));
        lenient().when(binanceService.getMyActualBalance()).thenReturn(new BigDecimal("0.1"));
    }

    @Test
    void shouldFinishWithoutExceptionWhenThereIsNoProblem() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"));
        when(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).thenReturn(List.of());

        assertThatCode(() -> cryptoTrader.startTrading()).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenThereAreOrphanBalances() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"));
        when(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).thenReturn(List.of(ORPHAN));

        assertThatThrownBy(() -> cryptoTrader.startTrading())
            .isInstanceOf(BinanceApiException.class)
            .hasMessage("Orphan balances: " + ORPHAN + ".");
    }

    @Test
    void shouldReportLowerAmountTogetherWithOrphanBalances() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"), new BigDecimal("0.04"));
        when(orphanBalanceChecker.findOrphanBalances(exchangeInfo)).thenReturn(List.of(ORPHAN));

        assertThatThrownBy(() -> cryptoTrader.startTrading())
            .isInstanceOf(BinanceApiException.class)
            .hasMessageStartingWith("New amount lower than before trading!")
            .hasMessageEndingWith("Orphan balances: " + ORPHAN + ".");
    }
}
