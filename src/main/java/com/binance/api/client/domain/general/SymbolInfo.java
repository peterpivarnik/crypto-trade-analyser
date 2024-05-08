package com.binance.api.client.domain.general;

import static com.binance.api.client.constant.BinanceApiConstants.TO_STRING_BUILDER_STYLE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Symbol information (base/quote).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolInfo {

  private String symbol;

  private SymbolStatus status;

  private List<SymbolFilter> filters;

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public SymbolStatus getStatus() {
    return status;
  }

  public void setStatus(SymbolStatus status) {
    this.status = status;
  }

  public List<SymbolFilter> getFilters() {
    return filters;
  }

  public void setFilters(List<SymbolFilter> filters) {
    this.filters = filters;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, TO_STRING_BUILDER_STYLE)
        .append("symbol", symbol)
        .append("status", status)
        .append("filters", filters)
        .toString();
  }
}
