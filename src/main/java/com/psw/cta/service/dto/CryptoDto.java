package com.psw.cta.service.dto;

import com.google.gson.internal.LinkedTreeMap;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@ToString
public class CryptoDto {

    public CryptoDto(BinanceExchangeSymbol binanceExchangeSymbol) {
        this.binanceExchangeSymbol = binanceExchangeSymbol;
    }

    private List<BinanceCandlestick> fifteenMinutesCandleStickData;
    private LinkedTreeMap<String, Object> ticker24hr;
    private LinkedTreeMap<String, Object> depth20;
    private BinanceExchangeSymbol binanceExchangeSymbol;

    private BigDecimal currentPrice;
    private BigDecimal volume;
    private BigDecimal sumDiffsPerc;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal weight;
}
