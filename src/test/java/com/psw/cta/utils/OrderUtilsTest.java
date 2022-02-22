package com.psw.cta.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
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
    void shouldReturnPriceToSellHigherBy5PercentWhenOrderBtcAmountMoreThanHalfOfMaxOrderBtcAmount() {
        BigDecimal orderBtcAmount = new BigDecimal("1");
        BigDecimal currentPrice = new BigDecimal("1");
        BigDecimal orderPrice = new BigDecimal("1");

        BigDecimal priceToSell = OrderUtils.calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("1.005");
    }

    @Test
    void shouldReturnPriceToSellLowerByQuarterWhenOrderBtcAmountLessThanHalfOfMaxOrderBtcAmount() {
        BigDecimal orderBtcAmount = new BigDecimal("0.005");
        BigDecimal currentPrice = new BigDecimal("0.1");
        BigDecimal orderPrice = new BigDecimal("0.9");

        BigDecimal priceToSell = OrderUtils.calculatePriceToSell(orderPrice, currentPrice, orderBtcAmount);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("0.7");
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

    @Test
    void shouldReturnQuantityFromOrder() {
        Order order = new Order();
        order.setOrigQty("25");
        order.setExecutedQty("10");

        BigDecimal quantityFromOrder = OrderUtils.getQuantityFromOrder(order);

        assertThat(quantityFromOrder.stripTrailingZeros().toPlainString()).isEqualTo("15");
    }
}