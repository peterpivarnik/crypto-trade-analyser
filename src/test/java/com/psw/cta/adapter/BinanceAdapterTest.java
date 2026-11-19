package com.psw.cta.adapter;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.PRICE_FILTER;
import static com.psw.cta.dto.binance.OrderSide.SELL;
import static com.psw.cta.dto.binance.OrderType.LIMIT;
import static com.psw.cta.dto.binance.TimeInForce.GTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.AssetBalance;
import com.psw.cta.dto.binance.FilterType;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.SymbolFilter;
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

    @Test
    void shouldKeepSellPriceThatIsAlreadyOnTick() {
        useEthFiltersAndBalance("1");

        binanceAdapter.placeSellOrder(symbolInfo, new BigDecimal("0.02857"), new BigDecimal("1"));

        verify(binanceClient).newOrder(SYMBOL, SELL, LIMIT, GTC, "1", "0.02857");
    }

    @Test
    void shouldRoundSellPriceUpToNextTick() {
        useEthFiltersAndBalance("1");

        binanceAdapter.placeSellOrder(symbolInfo, new BigDecimal("0.028573"), new BigDecimal("1"));

        verify(binanceClient).newOrder(eq(SYMBOL), eq(SELL), eq(LIMIT), eq(GTC), eq("1"), eq("0.028580"));
    }

    @Test
    void shouldExtractSmallPartAtOrderPriceAndRestOneTickHigher() {
        useEthFiltersAndBalance("0.2278");
        when(orderWrapper.getQuantity()).thenReturn(new BigDecimal("0.2278"));
        when(orderWrapper.getOrderPrice()).thenReturn(new BigDecimal("0.02857"));

        binanceAdapter.extractTwoSellOrders(symbolInfo, orderWrapper);

        verify(binanceClient).newOrder(eq(SYMBOL), eq(SELL), eq(LIMIT), eq(GTC), any(), eq("0.02857"));
        verify(binanceClient).newOrder(eq(SYMBOL), eq(SELL), eq(LIMIT), eq(GTC), any(), eq("0.02858"));
    }

    private void useEthFiltersAndBalance(String balance) {
        SymbolFilter priceFilter = filter(PRICE_FILTER);
        lenient().when(priceFilter.getTickSize()).thenReturn("0.00001");
        SymbolFilter lotSizeFilter = filter(LOT_SIZE);
        lenient().when(lotSizeFilter.getStepSize()).thenReturn("0.001");
        lenient().when(lotSizeFilter.getMinQty()).thenReturn("0.001");
        SymbolFilter notionalFilter = filter(NOTIONAL);
        lenient().when(notionalFilter.getMinNotional()).thenReturn("0.0001");
        symbolInfo.setFilters(List.of(priceFilter, lotSizeFilter, notionalFilter));

        AssetBalance assetBalance = new AssetBalance();
        assetBalance.setAsset("AAA");
        assetBalance.setFree(balance);
        Account account = new Account();
        account.setBalances(List.of(assetBalance));
        when(binanceClient.getAccount()).thenReturn(account);
    }

    private SymbolFilter filter(FilterType filterType) {
        SymbolFilter filter = mock(SymbolFilter.class);
        lenient().when(filter.getFilterType()).thenReturn(filterType);
        return filter;
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
