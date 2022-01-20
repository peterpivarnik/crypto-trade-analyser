package com.psw.cta.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.binance.api.client.domain.account.Order;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @InjectMocks
    private Utils utils;

    @Test
    void shouldOrderByOriginalQuantity() {
        Order order1 = createOrder("10", "0", "10", 10L);
        Order order2 = createOrder("20", "0", "10", 10L);
        Order order3 = createOrder("50", "0", "10", 10L);

        Comparator<Order> orderComparator = utils.getOrderComparator();

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

        Comparator<Order> orderComparator = utils.getOrderComparator();

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

        Comparator<Order> orderComparator = utils.getOrderComparator();

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

        Comparator<Order> orderComparator = utils.getOrderComparator();

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


}