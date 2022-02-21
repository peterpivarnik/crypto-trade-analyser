package com.psw.cta.utils;

import static com.psw.cta.utils.CryptoUtils.getVolume;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.binance.api.client.domain.general.SymbolInfo;
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

}