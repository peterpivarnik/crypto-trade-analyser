package com.psw.cta.processor.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.service.RepeatTradingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepeatTradingServiceTest {

    private static final String SYMBOL = "OLDBTC";

    @Mock
    private BinanceAdapter binanceAdapter;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;
    @Mock
    private OrderWrapper orderWrapper;
    @Mock
    private Order order;
    @Mock
    private SymbolInfo symbolInfo;
    @Mock
    private NewOrderResponse orderResponse;

    private RepeatTradingService repeatTradingService;

    @BeforeEach
    void setUp() {
        repeatTradingService = new RepeatTradingService(binanceAdapter, logger);
        lenient().when(orderWrapper.getOrder()).thenReturn(order);
        lenient().when(order.getSymbol()).thenReturn(SYMBOL);
        lenient().when(orderWrapper.getQuantity()).thenReturn(BigDecimal.TEN);
        lenient().when(exchangeInfo.getSymbolInfo(SYMBOL)).thenReturn(symbolInfo);
    }

    @Test
    void shouldBuyBeforeCancellingOrderAndSellDoubledQuantity() {
        BigDecimal newPrice = new BigDecimal("0.0002");
        when(binanceAdapter.buyWithQuantityRoundedUp(symbolInfo, BigDecimal.TEN)).thenReturn(orderResponse);
        when(binanceAdapter.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper)).thenReturn(newPrice);

        repeatTradingService.rebuySingleOrder(orderWrapper, (service, wrapper) -> true, exchangeInfo);

        InOrder inOrder = inOrder(binanceAdapter);
        inOrder.verify(binanceAdapter).buyWithQuantityRoundedUp(symbolInfo, BigDecimal.TEN);
        inOrder.verify(binanceAdapter).cancelOrder(orderWrapper);
        inOrder.verify(binanceAdapter).placeSellOrder(symbolInfo, newPrice, new BigDecimal("20"));
    }

    @Test
    void shouldKeepOrderWhenBuyFails() {
        when(binanceAdapter.buyWithQuantityRoundedUp(any(), any())).thenThrow(new BinanceApiException("No balance"));

        assertThatThrownBy(() -> repeatTradingService.rebuySingleOrder(orderWrapper,
                                                                       (service, wrapper) -> true,
                                                                       exchangeInfo))
            .isInstanceOf(BinanceApiException.class);

        verify(binanceAdapter, never()).cancelOrder(any());
    }

    @Test
    void shouldDoNothingWhenThereIsNotEnoughBtc() {
        repeatTradingService.rebuySingleOrder(orderWrapper, (service, wrapper) -> false, exchangeInfo);

        verifyNoInteractions(binanceAdapter);
    }
}
