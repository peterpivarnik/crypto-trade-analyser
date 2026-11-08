package com.psw.cta.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.SymbolInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CryptoTest {

    @Test
    void shouldCalculateSlopeAsPercentageOfMeanPrice() {
        Crypto crypto = crypto("100", "101", "102", "103", "104").calculateSlopeData();

        assertThat(crypto.getSlopePercentagePerDay()).isEqualByComparingTo("0.98039216");
    }

    @Test
    void shouldCalculateSameSlopeForCryptosWithDifferentPrices() {
        Crypto cheap = crypto("0.00000100", "0.00000101", "0.00000102", "0.00000103", "0.00000104")
            .calculateSlopeData();
        Crypto expensive = crypto("0.0100", "0.0101", "0.0102", "0.0103", "0.0104").calculateSlopeData();

        assertThat(cheap.getSlopePercentagePerDay()).isEqualByComparingTo(expensive.getSlopePercentagePerDay());
    }

    @Test
    void shouldKeepPrecisionOfAveragePriceForCheapCryptos() {
        Crypto cheap = cryptoWithSpread("0.00000101", "0.00000103", "0.00000105", "0.00000107");
        Crypto expensive = cryptoWithSpread("0.0101", "0.0103", "0.0105", "0.0107");

        assertThat(cheap.calculateSlopeData().getSlopePercentagePerDay())
            .isEqualByComparingTo(expensive.calculateSlopeData().getSlopePercentagePerDay());
    }

    @Test
    void shouldReturnZeroSlopeForFlatPrice() {
        Crypto crypto = crypto("0.001", "0.001", "0.001").calculateSlopeData();

        assertThat(crypto.getSlopePercentagePerDay()).isEqualByComparingTo("0");
    }

    private Crypto crypto(String... prices) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol("AAABTC");
        List<Candlestick> candlesticks = Stream.of(prices).map(this::candlestick).toList();
        return new Crypto(symbolInfo).setThreeMonthsCandleStickData(candlesticks);
    }

    private Crypto cryptoWithSpread(String... lowPrices) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol("AAABTC");
        List<Candlestick> candlesticks = Stream.of(lowPrices)
                                               .map(low -> {
                                                   Candlestick candlestick = candlestick(low);
                                                   String high = new BigDecimal(low)
                                                       .multiply(new BigDecimal("1.01"))
                                                       .toPlainString();
                                                   candlestick.setHigh(high);
                                                   candlestick.setClose(high);
                                                   return candlestick;
                                               })
                                               .toList();
        return new Crypto(symbolInfo).setThreeMonthsCandleStickData(candlesticks);
    }

    private Candlestick candlestick(String price) {
        Candlestick candlestick = new Candlestick();
        candlestick.setOpen(price);
        candlestick.setClose(price);
        candlestick.setHigh(price);
        candlestick.setLow(price);
        return candlestick;
    }
}
