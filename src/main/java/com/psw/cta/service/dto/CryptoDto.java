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
    private LinkedTreeMap<String, Object> depth20;
    private BigDecimal ratio;
    private BigDecimal lastHourAverage;
    private BigDecimal nextDayMaxPrice;
    private BigDecimal sumDiffsPerc2h;
    private BigDecimal sumDiffsPerc5h;
    private BigDecimal sumDiffsPerc10h;
    private BigDecimal sumDiffsPerc24h;
    private BigDecimal priceToSell2h;
    private BigDecimal priceToSell5h;
    private BigDecimal priceToSell10h;
    private BigDecimal priceToSell24h;
    private BigDecimal priceToSellPercentage2h;
    private BigDecimal priceToSellPercentage5h;
    private BigDecimal priceToSellPercentage10h;
    private BigDecimal priceToSellPercentage24h;
    private BigDecimal weight2h;
    private BigDecimal weight5h;
    private BigDecimal weight10h;
    private BigDecimal weight24h;
}
