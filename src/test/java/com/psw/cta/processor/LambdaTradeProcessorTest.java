package com.psw.cta.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.processor.trade.AcquireProcessor;
import com.psw.cta.processor.trade.CancelProcessor;
import com.psw.cta.processor.trade.CryptoProcessor;
import com.psw.cta.processor.trade.ExtractProcessor;
import com.psw.cta.processor.trade.RepeatTradingProcessor;
import com.psw.cta.processor.trade.SplitProcessor;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LambdaTradeProcessorTest {

    private static final String SYMBOL = "OLDBTC";

    @Mock
    private BinanceService binanceService;
    @Mock
    private CryptoProcessor cryptoProcessor;
    @Mock
    private SplitProcessor splitProcessor;
    @Mock
    private AcquireProcessor acquireProcessor;
    @Mock
    private RepeatTradingProcessor repeatTradingProcessor;
    @Mock
    private ExtractProcessor extractProcessor;
    @Mock
    private CancelProcessor cancelProcessor;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private LambdaTradeProcessor tradeProcessor;

    @BeforeEach
    void setUp() {
        tradeProcessor = new LambdaTradeProcessor(binanceService,
                                                  cryptoProcessor,
                                                  splitProcessor,
                                                  acquireProcessor,
                                                  repeatTradingProcessor,
                                                  extractProcessor,
                                                  cancelProcessor,
                                                  List.of(SYMBOL),
                                                  logger);
        lenient().when(binanceService.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        lenient().when(binanceService.getCandlesticks(any(), any(), any())).thenReturn(List.of(candlestick()));
    }

    @Test
    void shouldSellDelistedOrderWhenThereIsNoCryptoToSplitInto() {
        when(splitProcessor.hasCryptosToBuy(any(), any())).thenReturn(false);

        trade();

        verify(cancelProcessor).cancelAndSell(any(), eq(exchangeInfo));
        verify(splitProcessor, never()).splitCancelledOrder(any(), anyString(), any(), any(), any());
    }

    @Test
    void shouldSplitDelistedOrderWhenThereIsCryptoToSplitInto() {
        when(splitProcessor.hasCryptosToBuy(any(), any())).thenReturn(true);

        trade();

        verify(splitProcessor).splitCancelledOrder(any(), eq(SYMBOL), eq(exchangeInfo), any(), any());
        verify(cancelProcessor, never()).cancelAndSell(any(), any());
    }

    @Test
    void shouldSellDelistedOrderWithHighLossInsteadOfRebuyingIt() {
        when(binanceService.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.00005"));

        trade();

        verify(cancelProcessor).cancelAndSell(any(), eq(exchangeInfo));
        verify(repeatTradingProcessor, never()).rebuySingleOrder(any(), any(), any());
    }

    private void trade() {
        tradeProcessor.trade(List.of(order()),
                             Map.of(SYMBOL, new BigDecimal("0.0011")),
                             BigDecimal.ONE,
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             new BigDecimal("2"),
                             0);
    }

    private Candlestick candlestick() {
        Candlestick candlestick = new Candlestick();
        candlestick.setHigh("0.00011");
        candlestick.setLow("0.00009");
        return candlestick;
    }

    private Order order() {
        Order order = new Order();
        order.setSymbol(SYMBOL);
        order.setPrice("0.00011");
        order.setOrigQty("10");
        order.setExecutedQty("0");
        order.setTime(System.currentTimeMillis());
        return order;
    }
}
