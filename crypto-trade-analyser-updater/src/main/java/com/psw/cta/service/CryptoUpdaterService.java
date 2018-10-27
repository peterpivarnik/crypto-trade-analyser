package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.*;
import com.psw.cta.repository.CryptoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.webcerebrium.binance.datatype.BinanceInterval.ONE_DAY;

@Component
public class CryptoUpdaterService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Async
    @Time
    @Transactional
    @Scheduled(cron = "5 * * * * ?")
    void updateAll() throws BinanceApiException {
        BinanceApi api = new BinanceApi();
        BinanceExchangeInfo binanceExchangeInfo = api.exchangeInfo();
        binanceExchangeInfo.getSymbols().stream()
                .map(BinanceExchangeSymbol::getSymbol)
                .map(BinanceSymbol::getSymbol)
                .forEach(symbol -> saveData(api, symbol));
    }

    private void saveData(BinanceApi api, String symbol) {
        List<BinanceCandlestick> klines;
        try {
            klines = api.klines(new BinanceSymbol(symbol), ONE_DAY, 1, null);
        } catch (BinanceApiException e) {
            throw new RuntimeException("wrong symbol", e);
        }
        BinanceCandlestick binanceCandlestick = klines.get(0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime beforeOneDay = now.minusDays(1);
        cryptoRepository.update(binanceCandlestick.getHigh(), symbol, beforeOneDay);
    }
}

