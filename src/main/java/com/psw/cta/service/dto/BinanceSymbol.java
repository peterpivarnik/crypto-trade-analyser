package com.psw.cta.service.dto;

import com.psw.cta.exception.CryptoTradeAnalyserException;
import org.apache.logging.log4j.util.Strings;

public class BinanceSymbol {

    String symbol;

    public BinanceSymbol(String symbol) throws CryptoTradeAnalyserException {
        // sanitizing symbol, preventing from common user-input errors
        if (Strings.isBlank(symbol)) {
            throw new CryptoTradeAnalyserException("Symbol cannot be empty. Example: BQXBTC");
        }
        if (symbol.contains(" ")) {
            throw new CryptoTradeAnalyserException("Symbol cannot contain spaces. Example: BQXBTC");
        }
        this.symbol = symbol.replace("_", "").replace("-", "").toUpperCase();
    }

    public String getSymbol() {
        return this.symbol;
    }

    public static BinanceSymbol valueOf(String s) throws CryptoTradeAnalyserException {
        return new BinanceSymbol(s);
    }

}

