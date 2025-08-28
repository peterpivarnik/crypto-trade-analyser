package com.psw.cta.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Filters define trading rules on a symbol or an exchange.
 * Filters come in two forms: symbol filters and exchange filters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum FilterType {
  MAX_POSITION,
  PRICE_FILTER,
  T_PLUS_SELL,
  LOT_SIZE,
  MAX_NUM_ORDERS,
  MIN_NOTIONAL,
  MAX_NUM_ALGO_ORDERS,
  EXCHANGE_MAX_NUM_ORDERS,
  EXCHANGE_MAX_NUM_ALGO_ORDERS,
  ICEBERG_PARTS,
  MARKET_LOT_SIZE,
  PERCENT_PRICE,
  MAX_NUM_ICEBERG_ORDERS,
  EXCHANGE_MAX_NUM_ICEBERG_ORDERS,
  TRAILING_DELTA,
  PERCENT_PRICE_BY_SIDE,
  NOTIONAL,
  MAX_NUM_ORDER_LISTS,
  EXCHANGE_MAX_NUM_ORDER_LISTS,
  MAX_NUM_ORDER_AMENDS,
  MAX_ASSET,
  NON_REPRESENTABLE
}
