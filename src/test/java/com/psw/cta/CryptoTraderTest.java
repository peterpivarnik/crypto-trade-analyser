package com.psw.cta;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.service.BnbTradeService;
import com.psw.cta.service.OrphanBalanceService;
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
    private BinanceAdapter binanceAdapter;
    @Mock
    private BnbTradeService bnbTradeService;
    @Mock
    private LambdaTradeProcessor tradeProcessor;
    @Mock
    private OrphanBalanceService orphanBalanceService;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private CryptoTrader cryptoTrader;

    @BeforeEach
    void setUp() {
        cryptoTrader = new CryptoTrader(binanceAdapter,
                                        bnbTradeService,
                                        tradeProcessor,
                                        orphanBalanceService,
                                        logger);
        lenient().when(binanceAdapter.getOpenOrders()).thenReturn(List.of());
        lenient().when(binanceAdapter.getExchangeInfo()).thenReturn(exchangeInfo);
        lenient().when(binanceAdapter.getCurrentPrice(any())).thenReturn(new BigDecimal("0.009"));
        lenient().when(bnbTradeService.buyBnB(any(), any())).thenReturn(new BigDecimal("2"));
        lenient().when(binanceAdapter.getMyActualBalance()).thenReturn(new BigDecimal("0.1"));
    }

    @Test
    void shouldFinishWithoutExceptionWhenThereIsNoProblem() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"));
        when(orphanBalanceService.findOrphanBalances(exchangeInfo)).thenReturn(List.of());

        assertThatCode(() -> cryptoTrader.startTrading()).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenThereAreOrphanBalances() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"));
        when(orphanBalanceService.findOrphanBalances(exchangeInfo)).thenReturn(List.of(ORPHAN));

        assertThatThrownBy(() -> cryptoTrader.startTrading())
            .isInstanceOf(BinanceApiException.class)
            .hasMessage("Orphan balances: " + ORPHAN + ".");
    }

    @Test
    void shouldReportLowerAmountTogetherWithOrphanBalances() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.05"), new BigDecimal("0.04"));
        when(orphanBalanceService.findOrphanBalances(exchangeInfo)).thenReturn(List.of(ORPHAN));

        assertThatThrownBy(() -> cryptoTrader.startTrading())
            .isInstanceOf(BinanceApiException.class)
            .hasMessageStartingWith("New amount lower than before trading!")
            .hasMessageEndingWith("Orphan balances: " + ORPHAN + ".");
    }
}
