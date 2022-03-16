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
    void shouldCalculateMinPriceToSellWhenBtcAmountHighAndRatioHigh() {
        BigDecimal orderBtcAmount = new BigDecimal("0.15");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.75");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.004209"));
    }

    @Test
    void shouldCalculateMaxPriceToSellWhenBtcAmountLowAndRatioLow() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0001");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.50");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.00474"));
    }

    @Test
    void shouldCalculateMediumPriceToSellWhenBtcAmountHighAndRatioLow() {
        BigDecimal orderBtcAmount = new BigDecimal("0.15");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.50");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.004474"));
    }

    @Test
    void shouldCalculateMediumPriceToSellWhenBtcAmountLowAndRatioHigh() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0001");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.75");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.004474"));
    }

    @Test
    void shouldCalculateMinPriceToSellWhenBtcAmountAndRatioExtremelHigh() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.004209"));
    }

    @Test
    void shouldCalculateMaxPriceToSellWhenBtcAmountAndRatioExtremelyLow() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0001");
        BigDecimal currentPrice = new BigDecimal("0.00240000");
        BigDecimal orderPrice = new BigDecimal("0.00600000");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.01");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.004739"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesAreTheSame() {
        BigDecimal orderBtcAmount = new BigDecimal("0.0001");
        BigDecimal currentPrice = new BigDecimal("0.00654300");
        BigDecimal orderPrice = new BigDecimal("0.00654300");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("0.01");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesAreTheSame() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654300");
        BigDecimal orderPrice = new BigDecimal("0.00654300");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByOne() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654300");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006542"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByOne() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654300");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006542"));
    }


    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByTwo() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654400");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByTwo() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654400");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByThree() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654500");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    @Test
    void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByThree() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("0.00654200");
        BigDecimal orderPrice = new BigDecimal("0.00654500");
        SymbolInfo symbolInfo = createSymbolInfo();
        BigDecimal totalBalanceToBtcBalanceRatio = new BigDecimal("1");

        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount, symbolInfo, totalBalanceToBtcBalanceRatio);

        assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    }

    private SymbolInfo createSymbolInfo() {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setFilters(createFilters());
        return symbolInfo;
    }

    private List<SymbolFilter> createFilters() {
        List<SymbolFilter> filters = new ArrayList<>();
        filters.add(createFilter());
        return filters;
    }

    private SymbolFilter createFilter() {
        SymbolFilter symbolFilter = new SymbolFilter();
        symbolFilter.setTickSize("0.000001");
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