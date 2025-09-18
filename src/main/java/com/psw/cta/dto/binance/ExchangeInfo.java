package com.psw.cta.dto.binance;

import static com.psw.cta.utils.BinanceApiConstants.TO_STRING_BUILDER_STYLE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psw.cta.exception.BinanceApiException;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Current exchange trading rules and symbol information.
 * <a href="https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md">documentation</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeInfo {

    private List<SymbolInfo> symbols;

    public List<SymbolInfo> getSymbols() {
        return symbols;
    }

    /**
     * Returns Symbol information.
     *
     * @param symbol the symbol to obtain information for (e.g. ETHBTC)
     * @return symbol exchange information
     */
    public SymbolInfo getSymbolInfo(String symbol) {
        return symbols.stream()
                      .filter(symbolInfo -> symbolInfo.getSymbol().equals(symbol))
                      .findFirst()
                      .orElseThrow(() -> new BinanceApiException(
                          "Unable to obtain information for symbol " + symbol));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, TO_STRING_BUILDER_STYLE)
            .append("symbols", symbols)
            .toString();
    }
}
