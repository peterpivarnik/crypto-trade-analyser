package com.psw.cta.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderWrapperTest {

    private static final String SYMBOL = "AAABTC";
    private static final BigDecimal ORDER_PRICE = new BigDecimal("0.00011");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("0.0001");

    @Test
    void shouldTreatMissingCandlesticksAsFlatPrice() {
        OrderWrapper withoutCandlesticks = orderWrapper(List.of());
        OrderWrapper withFlatCandlestick = orderWrapper(List.of(candlestick(CURRENT_PRICE)));

        assertThat(withoutCandlesticks.getRemainWaitingTime())
            .isEqualByComparingTo(withFlatCandlestick.getRemainWaitingTime());
    }

    private OrderWrapper orderWrapper(List<Candlestick> candlesticks) {
        Order order = new Order();
        order.setSymbol(SYMBOL);
        order.setPrice(ORDER_PRICE.toPlainString());
        order.setOrigQty("10");
        order.setExecutedQty("0");
        return new OrderWrapper(order,
                                ORDER_PRICE,
                                CURRENT_PRICE,
                                BigDecimal.ONE,
                                new BigDecimal("2"),
                                OrderWrapper.calculatePricePercentage(CURRENT_PRICE, ORDER_PRICE),
                                Map.of(SYMBOL, new BigDecimal("0.0011")),
                                candlesticks,
                                new BigDecimal("5"));
    }

    private Candlestick candlestick(BigDecimal price) {
        Candlestick candlestick = new Candlestick();
        candlestick.setHigh(price.toPlainString());
        candlestick.setLow(price.toPlainString());
        return candlestick;
    }
}
