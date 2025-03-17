package com.psw.cta.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Filters define trading rules on a symbol or an exchange. Filters come in two forms:
 * symbol filters and exchange filters.
 * The PRICE_FILTER defines the price rules for a symbol.
 * The LOT_SIZE filter defines the quantity (aka "lots" in auction terms) rules for a symbol.
 * The MIN_NOTIONAL filter defines the minimum notional value allowed for an order on a symbol.
 * An order's notional value is the price * quantity.
 * The MAX_NUM_ORDERS filter defines the maximum number of orders an account is allowed to have open on a symbol.
 * Note that both "algo" orders and normal orders are counted for this filter.
 * The MAX_ALGO_ORDERS filter defines the maximum number of "algo" orders an account is allowed to have open on a
 * symbol. "Algo" orders are STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, and TAKE_PROFIT_LIMIT orders.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolFilter {

  private FilterType filterType;

  /**
   * Defines the intervals that a price/stopPrice can be increased/decreased by.
   */
  private String tickSize;

  /**
   * Defines the minimum quantity/icebergQty allowed.
   */
  private String minQty;

  /**
   * Defines the intervals that a quantity/icebergQty can be increased/decreased by.
   */
  private String stepSize;

  /**
   * Defines the minimum notional value allowed for an order on a symbol. An order's notional value is the
   * price * quantity.
   */
  private String minNotional;

  public FilterType getFilterType() {
    return filterType;
  }

  public String getTickSize() {
    return tickSize;
  }

  public String getMinQty() {
    return minQty;
  }

  public String getStepSize() {
    return stepSize;
  }

  public String getMinNotional() {
    return minNotional;
  }
}
