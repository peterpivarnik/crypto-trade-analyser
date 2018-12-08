package com.psw.cta.service.dto;

import com.psw.cta.exception.CryptoTradeAnalyserException;
import org.jsoup.helper.StringUtil;

public class BinanceSymbol {

    String symbol;

    public BinanceSymbol(String symbol)  throws CryptoTradeAnalyserException {
        // sanitizing symbol, preventing from common user-input errors
        if (StringUtil.isBlank(symbol)) {
            throw new CryptoTradeAnalyserException("Symbol cannot be empty. Example: BQXBTC");
        }
        if (symbol.contains(" ")) {
            throw new CryptoTradeAnalyserException("Symbol cannot contain spaces. Example: BQXBTC");
        }
        if (!symbol.endsWith("BTC") && !symbol.endsWith("ETH")&& !symbol.endsWith("BNB") && !symbol.endsWith("USDT")) {
            throw new CryptoTradeAnalyserException("Market Symbol should be ending with BTC, ETH, BNB or USDT. Example: BQXBTC. Provided: " + symbol);
        }
        this.symbol = symbol.replace("_", "").replace("-", "").toUpperCase();
    }

    public String get(){ return this.symbol; }

    public String getSymbol(){ return this.symbol; }

    public String toString() { return this.get(); }

    public static BinanceSymbol valueOf(String s) throws CryptoTradeAnalyserException {
        return new BinanceSymbol(s);
    }

    public static BinanceSymbol BTC(String pair) throws CryptoTradeAnalyserException {
        return BinanceSymbol.valueOf(pair.toUpperCase() + "BTC");
    }

    public static BinanceSymbol ETH(String pair) throws CryptoTradeAnalyserException {
        return BinanceSymbol.valueOf(pair.toUpperCase() + "ETH");
    }

    public static BinanceSymbol BNB(String pair) throws CryptoTradeAnalyserException {
        return BinanceSymbol.valueOf(pair.toUpperCase() + "BNB");
    }
    public static BinanceSymbol USDT(String pair) throws CryptoTradeAnalyserException {
        return BinanceSymbol.valueOf(pair.toUpperCase() + "USDT");
    }

    public boolean contains(String coin) {
        return (symbol.endsWith(coin.toUpperCase())) || (symbol.startsWith(coin.toUpperCase()));
    }

    public String getOpposite(String coin) {
        if (symbol.startsWith(coin)) {
            return symbol.substring((coin).length());
        }
        if (symbol.endsWith(coin)) {
            int index = symbol.length() - (coin).length();
            return symbol.substring(0, index);
        }
        return "";
    }

}

