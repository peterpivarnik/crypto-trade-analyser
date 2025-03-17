package com.psw.cta.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response object returned when an order is canceled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CancelOrderResponse {

  private String symbol;

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }
}
