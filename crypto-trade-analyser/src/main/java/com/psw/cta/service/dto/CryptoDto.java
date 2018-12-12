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
    private BigDecimal sumDiffsPerc1h;
    private BigDecimal sumDiffsPerc2h;
    private BigDecimal sumDiffsPerc5h;
    private BigDecimal priceToSell1h;
    private BigDecimal priceToSell2h;
    private BigDecimal priceToSell5h;
    private BigDecimal priceToSellPercentage1h;
    private BigDecimal priceToSellPercentage2h;
    private BigDecimal priceToSellPercentage5h;
    private BigDecimal weight1h;
    private BigDecimal weight2h;
    private BigDecimal weight5h;
}
