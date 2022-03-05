package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static org.assertj.core.api.Assertions.assertThat;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderUtilsTest {

    @Test
    void shouldCalculateOrderPrice() {
        String price = "0.256";
        Order order = new Order();
        order.setPrice(price);

        BigDecimal orderPrice = OrderUtils.calculateOrderPrice(order);

        assertThat(orderPrice).isEqualTo(price);
    }

    @Test
    void shouldCalculateOrderBtcAmount() {
        BigDecimal orderPrice = new BigDecimal("0.25");
        Order order = new Order();
        order.setOrigQty("20");
        order.setExecutedQty("0");

        BigDecimal orderBtcAmount = OrderUtils.calculateOrderBtcAmount(order, orderPrice);

        assertThat(orderBtcAmount).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void shouldReturnCurrentPriceWhenOrderPriceEqualCurrentPrice(){
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00000140");
        BigDecimal orderPrice = new BigDecimal("0.00000140");
        SymbolInfo symbolInfo = createSymbolInfo("0.0000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell).isEqualTo("0.00000140");
    }

    @Test
    void shouldReturnCurrentPriceWhenOrderPriceEqualCurrentPriceWithTickSize(){
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00000139");
        BigDecimal orderPrice = new BigDecimal("0.00000140");
        SymbolInfo symbolInfo = createSymbolInfo("0.00000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell).isEqualTo("0.00000139");
    }

    @Test
    void shouldReturnPriceToSellLowerByQuarterWhenOrderBtcAmountLessThan0point001() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0002016000000000");
        BigDecimal currentPrice = new BigDecimal("0.00001382");
        BigDecimal orderPrice = new BigDecimal("0.00001400");
        SymbolInfo symbolInfo = createSymbolInfo("0.00000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("0.00001396");
    }

    @Test
    void shouldReturnPriceToSellLowerByQuarterWhenOrderBtcAmountMoreThan0point001ButLessThan0point002() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0108599400000000");
        BigDecimal currentPrice = new BigDecimal("0.00000177");
        BigDecimal orderPrice = new BigDecimal("0.00000189");
        SymbolInfo symbolInfo = createSymbolInfo("0.00000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("0.00000185");
    }

    @Test
    void shouldReturnPriceToSellHigherBy5PercentWhenOrderBtcAmountMoreThan0point002() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0209677000000000");
        BigDecimal currentPrice = new BigDecimal("0.00000239");
        BigDecimal orderPrice = new BigDecimal("0.00000254");
        SymbolInfo symbolInfo = createSymbolInfo("0.0000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("0.0000024");
    }

    @Test
    void shouldReturnCurrentPriceWhenPriceToSellHigherThanOrderPrice() {
        BigDecimal orderBtcAmount = new BigDecimal("0.005");
        BigDecimal currentPrice = new BigDecimal("0.8");
        BigDecimal orderPrice = new BigDecimal("0.8");
        SymbolInfo symbolInfo = createSymbolInfo("0.0000001");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("0.8");
    }

    private SymbolInfo createSymbolInfo(String tickSize) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setFilters(createFilters(tickSize));
        return symbolInfo;
    }

    private List<SymbolFilter> createFilters(String tickSize) {
        List<SymbolFilter> filters = new ArrayList<>();
        filters.add(createFilter(tickSize));
        return filters;
    }

    private SymbolFilter createFilter(String tickSize) {
        SymbolFilter symbolFilter = new SymbolFilter();
        symbolFilter.setTickSize(tickSize);
        symbolFilter.setFilterType(PRICE_FILTER);
        return symbolFilter;
    }

    @Test
    void shouldCalculateMinWaitingTime() {
        BigDecimal totalSymbolAmount = new BigDecimal("0.11");
        BigDecimal orderBtcAmount = new BigDecimal("0.22");

        BigDecimal minWaitingTime = OrderUtils.calculateMinWaitingTime(totalSymbolAmount, orderBtcAmount);

        assertThat(minWaitingTime.stripTrailingZeros().toPlainString()).isEqualTo("160.2");
    }

    @Test
    void shouldCalculateActualWaitingTime() {
        Order order = new Order();
        order.setTime(ZonedDateTime.now().toInstant().toEpochMilli() - 1000 * 60 * 60);

        BigDecimal actualWaitingTime = OrderUtils.calculateActualWaitingTime(order);

        assertThat(actualWaitingTime.stripTrailingZeros().toPlainString()).isEqualTo("1");
    }
}