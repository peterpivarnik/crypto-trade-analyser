package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.initializeTradingService;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.CommonUtils.sleep;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CommonUtilsTest {

    @Test
    void shouldInitializeTradingService() {
        String apiKey = "apiKey";
        String apiSecret = "apiSecret";
        LambdaLogger logger = createLogger();

        TradingService tradingService = initializeTradingService(apiKey, apiSecret, logger);

        assertThat(tradingService).isNotNull();
    }

    private LambdaLogger createLogger() {
        return new LambdaLogger() {
            @Override public void log(String s) {

            }

            @Override public void log(byte[] bytes) {

            }
        };
    }


    @Test
    void shouldOrderByOriginalQuantity() {
        Order order1 = createOrder("10", "0", "10", 10L);
        Order order2 = createOrder("20", "0", "10", 10L);
        Order order3 = createOrder("50", "0", "10", 10L);

        Comparator<Order> orderComparator = getOrderComparator();

        List<Order> sortedOrders = Stream.of(order1, order2, order3)
                                         .sorted(orderComparator)
                                         .collect(Collectors.toList());
        assertThat(sortedOrders.get(0).getOrigQty()).isEqualTo("50");
    }

    @Test
    void shouldOrderByExecutedQuantityMinusExecutedQuantity() {
        Order order1 = createOrder("10", "0", "10", 10L);
        Order order2 = createOrder("20", "0", "10", 10L);
        Order order3 = createOrder("50", "45", "10", 10L);

        Comparator<Order> orderComparator = getOrderComparator();

        List<Order> sortedOrders = Stream.of(order1, order2, order3)
                                         .sorted(orderComparator)
                                         .collect(Collectors.toList());
        assertThat(sortedOrders.get(0).getOrigQty()).isEqualTo("20");
    }

    @Test
    void shouldOrderByBtcAmount() {
        Order order1 = createOrder("10", "0", "10", 10L);
        Order order2 = createOrder("20", "0", "20", 10L);
        Order order3 = createOrder("50", "30", "10", 10L);

        Comparator<Order> orderComparator = getOrderComparator();

        List<Order> sortedOrders = Stream.of(order1, order2, order3)
                                         .sorted(orderComparator)
                                         .collect(Collectors.toList());
        assertThat(sortedOrders.get(0).getOrigQty()).isEqualTo("20");
    }

    @Test
    void shouldOrderByTime() {
        Order order1 = createOrder("10", "0", "10", 10L);
        Order order2 = createOrder("20", "0", "10", 11L);
        Order order3 = createOrder("50", "30", "10", 10L);

        Comparator<Order> orderComparator = getOrderComparator();

        List<Order> sortedOrders = Stream.of(order1, order2, order3)
                                         .sorted(orderComparator)
                                         .collect(Collectors.toList());
        assertThat(sortedOrders.get(0).getOrigQty()).isEqualTo("50");
    }

    private Order createOrder(String origQty, String executedQty, String price, long time) {
        Order order = new Order();
        order.setOrigQty(origQty);
        order.setExecutedQty(executedQty);
        order.setPrice(price);
        order.setTime(time);
        return order;
    }

    @Test
    void shouldSleep() {
        int millis = 1000;
        LambdaLogger logger = createLogger();

        long start = currentTimeMillis();
        sleep(millis, logger);
        long end = currentTimeMillis();

        assertThat(end - start).isGreaterThanOrEqualTo(millis);
    }

    @Test
    void shouldReturnCorrectValueFromFilter() {
        FilterType filterType = MIN_NOTIONAL;
        String minNotional = "2";
        SymbolInfo symbolInfo = getSymbolInfo(filterType, minNotional);
        Function<SymbolFilter, String> symbolFilterFunction = SymbolFilter::getMinNotional;

        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);

        assertThat(valueFromFilter).isEqualTo(minNotional);
    }

    @Test
    void shouldThrowCryptoTraderExceptionWhenFilterDoNotExist() {
        String minNotional = "2";
        SymbolInfo symbolInfo = getSymbolInfo(MIN_NOTIONAL, minNotional);
        Function<SymbolFilter, String> symbolFilterFunction = SymbolFilter::getMinNotional;

        CryptoTraderException cryptoTraderException =
            assertThrows(CryptoTraderException.class, () -> getValueFromFilter(symbolInfo, PRICE_FILTER, symbolFilterFunction));

        assertThat(cryptoTraderException.getMessage()).isEqualTo("Value from filter PRICE_FILTER not found");
    }

    private SymbolInfo getSymbolInfo(FilterType filterType, String filterValue) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setFilters(getSymbolFilters(filterType, filterValue));
        return symbolInfo;
    }

    private List<SymbolFilter> getSymbolFilters(FilterType filterType, String filterValue) {
        List<SymbolFilter> filters = new ArrayList<>();
        filters.add(getSymbolFilter(filterType, filterValue));
        return filters;
    }

    private SymbolFilter getSymbolFilter(FilterType filterType, String filterValue) {
        SymbolFilter symbolFilter = new SymbolFilter();
        symbolFilter.setFilterType(filterType);
        symbolFilter.setMinNotional(filterValue);
        symbolFilter.setMinQty(filterValue);
        symbolFilter.setTickSize(filterValue);
        return symbolFilter;
    }

    @Test
    void shouldRoundAmount() {
        String filterValue = "0.00002";
        SymbolInfo symbolInfo = getSymbolInfo(LOT_SIZE, filterValue);
        BigDecimal amount = new BigDecimal("0.00005");

        BigDecimal roundedAmount = roundAmount(symbolInfo, amount);

        assertThat(roundedAmount).isEqualTo("0.00004");
    }

    @Test
    void shouldRoundPrice() {
        String filterValue = "0.00002";
        SymbolInfo symbolInfo = getSymbolInfo(PRICE_FILTER, filterValue);
        BigDecimal amount = new BigDecimal("0.00005");

        BigDecimal roundedAmount = roundPrice(symbolInfo, amount);

        assertThat(roundedAmount).isEqualTo("0.00004");
    }

    @Test
    void shouldCalculatePriceToSellPercentage() {
        BigDecimal orderPrice = new BigDecimal("50");
        BigDecimal priceToSell = new BigDecimal("40");

        BigDecimal priceToSellPercentage = calculatePricePercentage(priceToSell, orderPrice);

        assertThat(priceToSellPercentage.stripTrailingZeros().toPlainString()).isEqualTo("20");
    }
}