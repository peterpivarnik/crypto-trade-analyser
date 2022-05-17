package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static org.assertj.core.api.Assertions.assertThat;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
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
    BigDecimal myBtcBalance = new BigDecimal("0.1");
    BigDecimal actualBalance = new BigDecimal("1");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.004209"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMaxPriceToSellWhenBtcAmountLowAndRatioLow() {
    BigDecimal orderBtcAmount = new BigDecimal("0.0001");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("1");
    BigDecimal actualBalance = new BigDecimal("1");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.00474"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMediumPriceToSellWhenBtcAmountHighAndRatioLow() {
    BigDecimal orderBtcAmount = new BigDecimal("0.15");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("0.25");
    BigDecimal actualBalance = new BigDecimal("0.5");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.004327"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMediumPriceToSellFromRealData() {
    BigDecimal orderBtcAmount = new BigDecimal("0.0813726500000000");
    BigDecimal currentPrice = new BigDecimal("0.00081200");
    BigDecimal orderPrice = new BigDecimal("0.00106300");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("0.19862673");
    BigDecimal actualBalance = new BigDecimal("0.6740922083444");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.000945"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMediumPriceToSellWhenBtcAmountLowAndRatioHigh() {
    BigDecimal orderBtcAmount = new BigDecimal("0.0001");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("3");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.004666"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMinPriceToSellWhenBtcAmountAndRatioExtremelyHigh() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("0.1");
    BigDecimal actualBalance = new BigDecimal("1");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.004209"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculateMaxPriceToSellWhenBtcAmountAndRatioExtremelyLow() {
    BigDecimal orderBtcAmount = new BigDecimal("0.0001");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("1");
    BigDecimal actualBalance = new BigDecimal("1");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.00474"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesAreTheSame() {
    BigDecimal orderBtcAmount = new BigDecimal("0.0001");
    BigDecimal currentPrice = new BigDecimal("0.00654300");
    BigDecimal orderPrice = new BigDecimal("0.00654300");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("40");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesAreTheSame() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654300");
    BigDecimal orderPrice = new BigDecimal("0.00654300");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByOne() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654300");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006542"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByOne() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654300");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006542"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }


  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByTwo() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654400");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByTwo() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654400");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyLowAndPricesDifferByThree() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654500");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountAndRatioExtremelyHighAndPricesDifferByThree() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00654200");
    BigDecimal orderPrice = new BigDecimal("0.00654500");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("4");
    BigDecimal actualBalance = new BigDecimal("4");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.006543"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
  }

  @Test
  void shouldCalculatePriceToSellWhenBtcAmountExtremelyHigh() {
    BigDecimal orderBtcAmount = new BigDecimal("1");
    BigDecimal currentPrice = new BigDecimal("0.00240000");
    BigDecimal orderPrice = new BigDecimal("0.00600000");
    SymbolInfo symbolInfo = createSymbolInfo();
    BigDecimal myBtcBalance = new BigDecimal("100");
    BigDecimal actualBalance = new BigDecimal("100");

    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderBtcAmount,
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);

    assertThat(priceToSell).isEqualTo(new BigDecimal("0.004734"));
    assertThat(orderPrice).isGreaterThanOrEqualTo(priceToSell);
    assertThat(currentPrice).isLessThanOrEqualTo(priceToSell);
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

    BigDecimal minWaitingTime = OrderUtils.calculateMinWaitingTime(totalSymbolAmount,
                                                                   orderBtcAmount);

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
  void shouldReturnTrueWhenOrderPricePercentageLessThan10() {
    BigDecimal myBtcBalance = new BigDecimal("1");
    OrderWrapper orderWrapper = createOrderWrapper(new BigDecimal("1"), new BigDecimal("1"));

    Predicate<OrderWrapper> orderWrapperPredicate = OrderUtils.getOrderWrapperPredicate(myBtcBalance);

    assertThat(orderWrapperPredicate.test(orderWrapper)).isTrue();
  }

  @Test
  void shouldReturnTrueWhenOrderPricePercentageMoreThan10AndDoubleOrderBtcAmountLessThanMyBtcAmount() {
    BigDecimal myBtcBalance = new BigDecimal("15");
    OrderWrapper orderWrapper = createOrderWrapper(new BigDecimal("1"), new BigDecimal("15"));

    Predicate<OrderWrapper> orderWrapperPredicate = OrderUtils.getOrderWrapperPredicate(myBtcBalance);

    assertThat(orderWrapperPredicate.test(orderWrapper)).isTrue();
  }

  @Test
  void shouldReturnFalseWhenOrderPricePercentageMoreThan10AndDoubleOrderBtcAmountMoreThanMyBtcAmount() {
    BigDecimal myBtcBalance = new BigDecimal("15");
    OrderWrapper orderWrapper = createOrderWrapper(new BigDecimal("10"), new BigDecimal("15"));

    Predicate<OrderWrapper> orderWrapperPredicate = OrderUtils.getOrderWrapperPredicate(myBtcBalance);

    assertThat(orderWrapperPredicate.test(orderWrapper)).isFalse();
  }

  @Test
  void shouldReturnFalseWhenOrderBtcAmountLessThan0003() {
    BigDecimal myBtcBalance = new BigDecimal("15");
    OrderWrapper orderWrapper = createOrderWrapper(new BigDecimal("10"), new BigDecimal("0.0001"));

    Predicate<OrderWrapper> orderWrapperPredicate = OrderUtils.getOrderWrapperPredicate(myBtcBalance);

    assertThat(orderWrapperPredicate.test(orderWrapper)).isFalse();
  }

  private OrderWrapper createOrderWrapper(BigDecimal orderPricePercentage, BigDecimal orderBtcAmount) {
    OrderWrapper orderWrapper = new OrderWrapper(createOrder());
    orderWrapper.setOrderPricePercentage(orderPricePercentage);
    orderWrapper.setOrderBtcAmount(orderBtcAmount);
    return orderWrapper;
  }

  private Order createOrder() {
    return new Order();
  }
}