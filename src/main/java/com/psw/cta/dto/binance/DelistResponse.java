package com.psw.cta.dto.binance;

import java.util.List;

/**
 * Data Transfer Object (DTO) representing Binance delisting response.
 * Contains information about trading pairs that will be delisted from the exchange.
 */
public class DelistResponse {

    private long delistTime;
    private List<String> symbols;

    public long getDelistTime() {
        return delistTime;
    }

    public void setDelistTime(long delistTime) {
        this.delistTime = delistTime;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }
}
