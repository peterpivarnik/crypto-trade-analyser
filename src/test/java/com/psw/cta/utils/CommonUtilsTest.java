package com.psw.cta.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.FilterType;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.CryptoTraderException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.CommonUtils.roundPriceUp;
import static com.psw.cta.utils.CommonUtils.sleep;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonUtilsTest {

  @Test
  void shouldSleep() {
    int millis = 1000;
    LambdaLogger logger = createLogger();

    long start = currentTimeMillis();
    sleep(millis, logger);
    long end = currentTimeMillis();

    assertThat(end - start).isGreaterThanOrEqualTo(millis);
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
  void shouldReturnCorrectValueFromFilter() {
    FilterType filterType = MIN_NOTIONAL;
    String minNotional = "2";
    SymbolInfo symbolInfo = getSymbolInfo(filterType, minNotional);
    Function<SymbolFilter, String> symbolFilterFunction = SymbolFilter::getMinNotional;

    BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, symbolFilterFunction, filterType);

    assertThat(valueFromFilter).isEqualTo(minNotional);
  }

  @Test
  void shouldReturnCorrectValueFromFilterForNotionalFilterType() {
    FilterType filterType = NOTIONAL;
    String minNotional = "2";
    SymbolInfo symbolInfo = getSymbolInfo(filterType, minNotional);
    Function<SymbolFilter, String> symbolFilterFunction = SymbolFilter::getMinNotional;

    BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, symbolFilterFunction, filterType);

    assertThat(valueFromFilter).isEqualTo(minNotional);
  }

  @Test
  void shouldThrowCryptoTraderExceptionWhenFilterDoNotExist() {
    String minNotional = "2";
    SymbolInfo symbolInfo = getSymbolInfo(MIN_NOTIONAL, minNotional);
    Function<SymbolFilter, String> symbolFilterFunction = SymbolFilter::getMinNotional;

    CryptoTraderException cryptoTraderException = assertThrows(CryptoTraderException.class,
                                                               () -> getValueFromFilter(symbolInfo, symbolFilterFunction, PRICE_FILTER));

    assertThat(cryptoTraderException.getMessage()).isEqualTo("Value from filters [PRICE_FILTER] not found");
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
  void shouldReturnQuantityFromOrder() {
    Order order = new Order();
    order.setOrigQty("25");
    order.setExecutedQty("10");

    BigDecimal quantityFromOrder = getQuantity(order);

    assertThat(quantityFromOrder.stripTrailingZeros().toPlainString()).isEqualTo("15");
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