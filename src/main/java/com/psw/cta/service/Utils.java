package com.psw.cta.service;

import static java.util.Comparator.comparing;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

public class Utils {

    public Comparator<Order> getOrderComparator() {
        Function<Order, BigDecimal> quantityFunction = order -> new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
        Function<Order, BigDecimal> btcAmountFunction =
            order -> (new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()))).multiply(new BigDecimal(order.getPrice()));
        Function<Order, BigDecimal> timeFunction = order -> new BigDecimal(order.getTime());
        return comparing(quantityFunction)
            .reversed()
            .thenComparing(comparing(btcAmountFunction).reversed())
            .thenComparing(timeFunction);
    }
}
