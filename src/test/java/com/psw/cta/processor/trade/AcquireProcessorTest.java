package com.psw.cta.processor.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcquireProcessorTest {

    private static final BigDecimal CURRENT_PRICE = new BigDecimal("0.001");

    @Mock
    private BinanceService binanceService;
    @Mock
    private LambdaLogger logger;

    private AcquireProcessor acquireProcessor;

    @BeforeEach
    void setUp() {
        acquireProcessor = new AcquireProcessor(binanceService, logger);
        lenient().when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        lenient().when(binanceService.buyWithBtcs(any(), any())).thenReturn(BigDecimal.ONE);
    }

    @Test
    void shouldBuyCryptoPassingFiltersAndSkipCryptoWithExistingOrder() {
        when(binanceService.getCandleStickData(eq("NEWBTC"), any(), anyLong(), any())).thenReturn(risingCandles());

        List<String> acquired = acquireProcessor.initTrading(List.of(crypto("NEWBTC"), crypto("HELDBTC")),
                                                             Set.of("HELDBTC"));

        assertThat(acquired).containsExactly("NEWBTC");
        verify(binanceService, never()).getCandleStickData(eq("HELDBTC"), any(), anyLong(), any());
    }

    @Test
    void shouldReturnNothingWhenNoCryptoPassesFilters() {
        when(binanceService.getCandleStickData(eq("NEWBTC"), any(), anyLong(), any())).thenReturn(flatCandles());

        List<String> acquired = acquireProcessor.initTrading(List.of(crypto("NEWBTC")), Set.of());

        assertThat(acquired).isEmpty();
        verify(binanceService, never()).buyWithBtcs(any(), any());
    }

    @Test
    void shouldReturnNothingWhenThereIsNotEnoughBtc() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.0002"));
        when(binanceService.getCandleStickData(eq("NEWBTC"), any(), anyLong(), any())).thenReturn(risingCandles());

        List<String> acquired = acquireProcessor.initTrading(List.of(crypto("NEWBTC")), Set.of());

        assertThat(acquired).isEmpty();
        verify(binanceService, never()).buyWithBtcs(any(), any());
    }

    private Crypto crypto(String symbol) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol(symbol);
        return new Crypto(symbolInfo).setCurrentPrice(CURRENT_PRICE);
    }

    private List<Candlestick> risingCandles() {
        return IntStream.range(0, 8)
                        .mapToObj(index -> candlestick(index < 5 ? "1.010" : "1.012"))
                        .toList();
    }

    private List<Candlestick> flatCandles() {
        return IntStream.range(0, 8)
                        .mapToObj(index -> candlestick("1"))
                        .toList();
    }

    private Candlestick candlestick(String highMultiplier) {
        String price = CURRENT_PRICE.toPlainString();
        Candlestick candlestick = new Candlestick();
        candlestick.setOpen(price);
        candlestick.setClose(price);
        candlestick.setLow(price);
        candlestick.setHigh(CURRENT_PRICE.multiply(new BigDecimal(highMultiplier)).toPlainString());
        return candlestick;
    }
}
