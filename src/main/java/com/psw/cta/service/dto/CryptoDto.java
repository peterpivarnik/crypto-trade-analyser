package com.psw.cta.service.dto;

import com.google.gson.internal.LinkedTreeMap;
import com.psw.cta.entity.Crypto;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceExchangeSymbol;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class CryptoDto extends Crypto {

    private List<BinanceCandlestick> fifteenMinutesCandleStickData;
    private LinkedTreeMap<String, Object> ticker24hr;
    private LinkedTreeMap<String, Object> depth20;
    private BinanceExchangeSymbol binanceExchangeSymbol;

}
