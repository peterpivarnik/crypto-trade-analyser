package com.psw.cta.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.Trade;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BinanceAdapterTest {

    private static final String SYMBOL = "AAABTC";

    @Mock
    private BinanceClient binanceClient;
    @Mock
    private LambdaLogger logger;
    @Mock
    private OrderWrapper orderWrapper;

    private BinanceAdapter binanceAdapter;
    private SymbolInfo symbolInfo;

    @BeforeEach
    void setUp() {
        binanceAdapter = new BinanceAdapter(binanceClient, logger);
        symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol(SYMBOL);
        lenient().when(orderWrapper.getCurrentPrice()).thenReturn(new BigDecimal("0.0001"));
        lenient().when(orderWrapper.getPriceToSell()).thenReturn(new BigDecimal("0.00012"));
    }

    @Test
    void shouldCalculateNewPriceToSellFromOrderResponseWithoutLoadingTrades() {
        NewOrderResponse orderResponse = orderResponse("10", "0.00102");

        BigDecimal newPriceToSell = binanceAdapter.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper);

        assertThat(newPriceToSell).isEqualByComparingTo("0.000122");
        verify(binanceClient, never()).getMyTrades(any(), any());
    }

    @Test
    void shouldLoadTradesWhenOrderResponseHasNoBoughtBtcs() {
        NewOrderResponse orderResponse = orderResponse("10", null);
        when(binanceClient.getMyTrades(SYMBOL, "1")).thenReturn(List.of(trade("4", "0.0001"), trade("6", "0.000103")));

        BigDecimal newPriceToSell = binanceAdapter.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper);

        assertThat(newPriceToSell).isEqualByComparingTo("0.0001218");
    }

    private NewOrderResponse orderResponse(String executedQty, String cummulativeQuoteQty) {
        NewOrderResponse orderResponse = new NewOrderResponse();
        orderResponse.setOrderId(1L);
        orderResponse.setExecutedQty(executedQty);
        orderResponse.setCummulativeQuoteQty(cummulativeQuoteQty);
        return orderResponse;
    }

    private Trade trade(String qty, String price) {
        Trade trade = new Trade();
        trade.setQty(qty);
        trade.setPrice(price);
        return trade;
    }
}
