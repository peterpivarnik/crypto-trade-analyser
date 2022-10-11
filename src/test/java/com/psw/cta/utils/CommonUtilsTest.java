package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.CommonUtils.calculateMinNumberOfOrders;
import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.createTotalAmounts;
import static com.psw.cta.utils.CommonUtils.getAveragePrice;
import static com.psw.cta.utils.CommonUtils.getAveragePrices;
import static com.psw.cta.utils.CommonUtils.getCurrentPrice;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.getPriceCountToSlope;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.initializeTradingService;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.CommonUtils.roundPriceUp;
import static com.psw.cta.utils.CommonUtils.sleep;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
      @Override
      public void log(String s) {

      }

      @Override
      public void log(byte[] bytes) {

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

    CryptoTraderException cryptoTraderException = assertThrows(CryptoTraderException.class,
                                                               () -> getValueFromFilter(symbolInfo,
                                                                                        PRICE_FILTER,
                                                                                        symbolFilterFunction));

    assertThat(cryptoTraderException.getMessage()).isEqualTo(
        "Value from filter PRICE_FILTER not found");
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
  void shouldRoundPriceUp() {
    String filterValue = "0.00002";
    SymbolInfo symbolInfo = getSymbolInfo(PRICE_FILTER, filterValue);
    BigDecimal amount = new BigDecimal("0.00005");

    BigDecimal roundedAmount = roundPriceUp(symbolInfo, amount);

    assertThat(roundedAmount).isEqualTo("0.00006");
  }

  @Test
  void shouldReturnValidCountToSlope() {
    List<BigDecimal> averagePrices = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      averagePrices.add(new BigDecimal("" + i));
    }

    BigDecimal priceCountToSlope = getPriceCountToSlope(averagePrices);

    assertThat(priceCountToSlope.stripTrailingZeros().toPlainString()).isEqualTo("100");
  }

  @Test
  void shouldReturnAveragePrices() {
    List<Candlestick> threeMonthsCandleStickData = createThreeMonthsCandleStickData();

    List<BigDecimal> averagePrices = getAveragePrices(threeMonthsCandleStickData);

    assertThat(averagePrices).hasSize(99);
    for (int i = 0; i < 99; i++) {
      assertThat(averagePrices.get(i)).isEqualByComparingTo(new BigDecimal(i));
    }
  }

  private List<Candlestick> createThreeMonthsCandleStickData() {
    List<Candlestick> candlesticks = new ArrayList<>();
    int size = 100;
    for (int i = 0; i < size; i++) {
      candlesticks.add(createCandleStick(i, size));
    }
    return candlesticks;
  }

  @Test
  void shouldGetAveragePrice() {
    Candlestick candlestick = new Candlestick();
    candlestick.setOpen("1");
    candlestick.setClose("2");
    candlestick.setHigh("3");
    candlestick.setLow("4");

    BigDecimal averagePrice = getAveragePrice(candlestick);

    assertThat(averagePrice.stripTrailingZeros()).isEqualTo("2.5");
  }

  private Candlestick createCandleStick(int filterValue, int size) {
    Candlestick candlestick = new Candlestick();
    candlestick.setOpen("" + filterValue);
    candlestick.setClose("" + filterValue);
    candlestick.setHigh("" + filterValue);
    candlestick.setLow("" + filterValue);
    candlestick.setOpenTime((long) size - (long) filterValue);
    return candlestick;
  }

  @Test
  void shouldCreateTotalAmounts() {
    String symbol1 = "symbol1";
    String symbol2 = "symbol2";
    Order order1 = getOrder(symbol1, 1);
    Order order2 = getOrder(symbol1, 2);
    Order order3 = getOrder(symbol2, 5);
    List<Order> openOrders = new ArrayList<>();
    openOrders.add(order1);
    openOrders.add(order2);
    openOrders.add(order3);

    Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);

    assertThat(totalAmounts).hasSize(2)
                            .containsKey(symbol1);
    assertThat(totalAmounts.get(symbol1)).isEqualByComparingTo(new BigDecimal("30"));
    assertThat(totalAmounts).containsKey(symbol2);
    assertThat(totalAmounts.get(symbol2)).isEqualByComparingTo(new BigDecimal("50"));
  }

  private Order getOrder(String symbol, int index) {
    Order order = new Order();
    order.setSymbol(symbol);
    order.setPrice("10");
    order.setOrigQty("" + index);
    order.setExecutedQty("0");
    return order;
  }

  @Test
  void shouldReturnQuantityFromOrder() {
    Order order = new Order();
    order.setOrigQty("25");
    order.setExecutedQty("10");

    BigDecimal quantityFromOrder = getQuantity(order);

    assertThat(quantityFromOrder.stripTrailingZeros().toPlainString()).isEqualTo("15");
  }

  @Test
  void shouldCalculateMinNumberOfOrdersFromMyBtcBalance() {
    BigDecimal myBtcBalance = new BigDecimal("4");

    int minNumberOfOrders = calculateMinNumberOfOrders(myBtcBalance);

    assertThat(minNumberOfOrders).isEqualTo(200);
  }

  @Test
  void shouldCalculateCurrentPrice() {
    OrderBook orderBook = createOrderBook(20);

    BigDecimal currentPrice = getCurrentPrice(orderBook);

    assertThat(currentPrice.stripTrailingZeros()).isEqualTo("5");
  }

  @Test
  void shouldThrowCryptoTraderExceptionWhenNoOrderBookEntryExist() {
    OrderBook orderBook = createOrderBook(0);

    CryptoTraderException cryptoTraderException = assertThrows(CryptoTraderException.class,
                                                               () -> getCurrentPrice(orderBook));

    assertThat(cryptoTraderException.getMessage()).isEqualTo("No price found!");
  }

  private OrderBook createOrderBook(int size) {
    OrderBook orderBook = new OrderBook();
    orderBook.setAsks(createAsks(size));
    return orderBook;
  }

  private List<OrderBookEntry> createAsks(int size) {
    List<OrderBookEntry> orderBookEntries = new ArrayList<>();
    for (int i = 5; i < size; i++) {
      orderBookEntries.add(createOrderBookEntries("" + i));
    }
    return orderBookEntries;
  }

  private OrderBookEntry createOrderBookEntries(String price) {
    OrderBookEntry orderBookEntry = new OrderBookEntry();
    orderBookEntry.setPrice(price);
    return orderBookEntry;
  }

  @Test
  void shouldCalculatePriceToSellPercentage() {
    BigDecimal orderPrice = new BigDecimal("50");
    BigDecimal priceToSell = new BigDecimal("40");

    BigDecimal priceToSellPercentage = calculatePricePercentage(priceToSell, orderPrice);

    assertThat(priceToSellPercentage.stripTrailingZeros().toPlainString()).isEqualTo("20");
  }

  @Test
  void shouldHaveBalanceForInitialTrading() {
    BigDecimal myBtcBalance = new BigDecimal("1");

    boolean haveBalance = haveBalanceForInitialTrading(myBtcBalance);

    assertThat(haveBalance).isTrue();
  }

  @Test
  void shouldNotHaveBalanceForInitialTrading() {
    BigDecimal myBtcBalance = new BigDecimal("0.0001");

    boolean haveBalance = haveBalanceForInitialTrading(myBtcBalance);

    assertThat(haveBalance).isFalse();
  }
}