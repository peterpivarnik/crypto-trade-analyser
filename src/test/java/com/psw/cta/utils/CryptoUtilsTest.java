package com.psw.cta.utils;

import static com.psw.cta.utils.CryptoUtils.calculateLastThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences10h;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences1h;
import static com.psw.cta.utils.CryptoUtils.getVolume;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

    @Test
    void shouldThrowCryptoTraderExceptionWhenTickerWithSymbolNotExist() {
        Crypto crypto = createCrypto("bbb");
        List<TickerStatistics> tickers = createTickerStatistics(null);

        CryptoTraderException thrownException = assertThrows(CryptoTraderException.class, () -> getVolume(crypto, tickers));

        assertThat(thrownException.getMessage()).isEqualTo("Ticker with symbol: bbb not found.");
    }

    @Test
    void shouldReturnVolumeWhenVolumeIsProperlySet() {
        Crypto crypto = createCrypto("aaa");
        String volume = "12";
        List<TickerStatistics> tickers = createTickerStatistics(volume);

        BigDecimal returnedVolume = getVolume(crypto, tickers);

        assertThat(returnedVolume).isEqualTo(volume);
    }

    private Crypto createCrypto(String aaa) {
        SymbolInfo symbolInfo = createSymbolInfo(aaa);
        return new Crypto(symbolInfo);
    }

    private SymbolInfo createSymbolInfo(String aaa) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol(aaa);
        return symbolInfo;
    }

    private List<TickerStatistics> createTickerStatistics(String volume) {
        List<TickerStatistics> tickers = new ArrayList<>();
        tickers.add(createTickerStatistic(volume));
        return tickers;
    }

    private TickerStatistics createTickerStatistic(String volume) {
        TickerStatistics ticker = new TickerStatistics();
        ticker.setSymbol("aaa");
        ticker.setVolume(volume);
        return ticker;
    }

    @Test
    void shouldCalculateLastThreeMaxAverage() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(10);

        BigDecimal lastThreeMaxAverage = calculateLastThreeHighAverage(fifteenMinutesCandleStickData);

        assertThat(lastThreeMaxAverage.stripTrailingZeros()).isEqualTo("8");
    }

    @Test
    void shouldCalculatePreviousThreeMaxAverage() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(10);

        BigDecimal lastThreeMaxAverage = calculatePreviousThreeHighAverage(fifteenMinutesCandleStickData);

        assertThat(lastThreeMaxAverage.stripTrailingZeros()).isEqualTo("5");
    }

    @Test
    void shouldCalculateSumDiffsPercent() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(10);
        BigDecimal currentPrice = new BigDecimal("10");

        BigDecimal sumDiffsPercent = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData, currentPrice);

        assertThat(sumDiffsPercent.stripTrailingZeros().toPlainString()).isEqualTo("100");
    }

    @Test
    void shouldReturnZeroWhenLessThanFourCandleSticks() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(2);
        BigDecimal currentPrice = new BigDecimal("10");

        BigDecimal sumDiffsPercent = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData, currentPrice);

        assertThat(sumDiffsPercent.stripTrailingZeros().toPlainString()).isEqualTo("0");
    }

    @Test
    void shouldCalculateSumDiffsPercent10h() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(45);
        BigDecimal currentPrice = new BigDecimal("10");

        BigDecimal sumDiffsPercent = calculateSumPercentageDifferences10h(fifteenMinutesCandleStickData, currentPrice);

        assertThat(sumDiffsPercent.stripTrailingZeros().toPlainString()).isEqualTo("6100");
    }

    @Test
    void shouldReturnZeroWhenLessThanFortyCandleSticks() {
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(2);
        BigDecimal currentPrice = new BigDecimal("10");

        BigDecimal sumDiffsPercent = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData, currentPrice);

        assertThat(sumDiffsPercent.stripTrailingZeros().toPlainString()).isEqualTo("0");
    }

    @Test
    void shouldCalculatePriceToSell() {
        BigDecimal currentPrice = new BigDecimal("0.5");
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(10);

        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo("4.75");
    }

    @Test
    void shouldReturnCurrentPriceWhenNotEnoughCandleSticks() {
        BigDecimal currentPrice = new BigDecimal("0.5");
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(1);

        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo(currentPrice);
    }

    @Test
    void shouldReturnCurrentPriceWhenNotMissingHighInCandleStick() {
        BigDecimal currentPrice = new BigDecimal("0.5");
        List<Candlestick> fifteenMinutesCandleStickData = createCandlesticks(0);

        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);

        assertThat(priceToSell.stripTrailingZeros()).isEqualTo(currentPrice);
    }

    private List<Candlestick> createCandlesticks(int numberOfCandleSticks) {
        List<Candlestick> fifteenMinutesCandleStickData = new ArrayList<>();
        for (int i = 0; i < numberOfCandleSticks; i++) {
            fifteenMinutesCandleStickData.add(createCandlestick(i));
        }
        return fifteenMinutesCandleStickData;
    }

    private Candlestick createCandlestick(int price) {
        Candlestick candleStick = new Candlestick();
        candleStick.setHigh("" + price);
        candleStick.setLow("" + price);
        candleStick.setOpen("" + price);
        candleStick.setClose("" + price);
        return candleStick;
    }
}