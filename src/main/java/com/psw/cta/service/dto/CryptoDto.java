package com.psw.cta.service.dto;

import com.google.gson.internal.LinkedTreeMap;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceExchangeSymbol;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@ToString
public class CryptoDto {

    private BinanceExchangeSymbol binanceExchangeSymbol;
    private BigDecimal currentPrice;
    private List<BinanceCandlestick> fifteenMinutesCandleStickData;
    private List<BinanceCandlestick> threeDaysCandleStickData;
    private BigDecimal fifteenMinutesMaxToCurrentDifferent;
    private BigDecimal fifteenMinutesPercentageLoss;
    private BigDecimal lastThreeDaysAveragePrice;
    private BigDecimal lastThreeDaysMaxPrice;
    private BigDecimal lastThreeDaysMinPrice;
    private BigDecimal lastThreeDaysMaxMinDiffPercent;
    private BigDecimal volume;
    private LinkedTreeMap<String, Object> ticker24hr;
    private BigDecimal weight;
    private LinkedTreeMap<String, Object> depth20;
    private BigDecimal ratio;
    private BigDecimal priceToSell;
}
