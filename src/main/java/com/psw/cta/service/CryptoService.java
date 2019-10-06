package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.SuccessRate;
import com.psw.cta.service.dto.BinanceCandlestick;
import com.psw.cta.service.dto.BinanceSymbol;
import com.psw.cta.service.factory.SuccessRateFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static com.psw.cta.service.dto.BinanceInterval.ONE_MIN;
import static java.time.temporal.ChronoUnit.*;

@Component
@Slf4j
public class CryptoService {

    private CryptoRepository cryptoRepository;
    private SuccessRateFactory successRateFactory;
    private BinanceService binanceService;

    public CryptoService(CryptoRepository cryptoRepository,
                         SuccessRateFactory successRateFactory,
                         BinanceService binanceService) {
        this.cryptoRepository = cryptoRepository;
        this.successRateFactory = successRateFactory;
        this.binanceService = binanceService;
    }

    @Transactional
    @Time
    SuccessRate getSuccessRate() {
        Instant now = Instant.now();
        Long before7Days = now.minus(7, DAYS).toEpochMilli();
        Long before8Days = now.minus(8, DAYS).toEpochMilli();
        double allStats = cryptoRepository.countByCreatedAtBetween(before8Days, before7Days);
        double nextDayValid = cryptoRepository.countByCreatedAtBetweenAndNextDayMaxPriceHigherOrEqualPriceToSell(
                before8Days,
                before7Days);
        double next2DysValid = cryptoRepository.countByCreatedAtBetweenAndNext2DayMaxPriceHigherOrEqualPriceToSell(
                before8Days,
                before7Days);
        double nextWeekValid = cryptoRepository.countByCreatedAtBetweenAndNextWeekMaxPriceHigherOrEqualPriceToSell(
                before8Days,
                before7Days);
        double oneDaySuccessRate = nextDayValid / allStats * 100;
        double twoDaySuccessRate = next2DysValid / allStats * 100;
        double oneWeekSuccessRate = nextWeekValid / allStats * 100;
        return successRateFactory.create(oneDaySuccessRate, twoDaySuccessRate, oneWeekSuccessRate);
    }

    @Time
    @Scheduled(cron = "0 */30 * * * ?")
    public void updateNextDayMaxPrice() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, DAYS);
        cryptoRepository.findUniqueSymbolsByCreatedAtGreaterThan(beforeOneDay.toEpochMilli())
                .forEach(symbol -> saveNextDayMaxPrice(symbol, now));

    }

    private void saveNextDayMaxPrice(String symbol, Instant now) {
        Instant before30Min = now.minus(30, MINUTES);
        Instant beforeOneDay = now.minus(1, DAYS);

        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        cryptoRepository.updateNextDayMaxPriceBySymbolAndCreatedAtBetween(lastFifteenMinuteMax,
                                                                          symbol,
                                                                          beforeOneDay.toEpochMilli(),
                                                                          before30Min.toEpochMilli());
    }

    @Time
    @Scheduled(cron = "0 0 * * * ?")
    public void updateNext2DayMaxPrice() {
        Instant now = Instant.now();
        Instant before2Days = now.minus(2, DAYS);
        cryptoRepository.findUniqueSymbolsByCreatedAtGreaterThan(before2Days.toEpochMilli())
                .forEach(symbol -> saveNext2DaysMaxPrice(symbol, now));
    }

    private void saveNext2DaysMaxPrice(String symbol, Instant now) {
        Instant before1Hour = now.minus(7, DAYS);
        Instant beforeTwoDays = now.minus(2, DAYS);

        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        cryptoRepository.updateNext2DayMaxPriceBySymbolAndCreatedAtBetween(lastFifteenMinuteMax,
                                                                           symbol,
                                                                           beforeTwoDays.toEpochMilli(),
                                                                           before1Hour.toEpochMilli());
    }

    @Time
    @Scheduled(cron = "0 0 */2 * * ?")
    public void updateNextWeekMaxPrice() {
        Instant now = Instant.now();
        Instant beforeOneWeek = now.minus(7, DAYS);
        cryptoRepository.findUniqueSymbolsByCreatedAtGreaterThan(beforeOneWeek.toEpochMilli())
                .forEach(symbol -> saveNextWeekMaxPrice(symbol, now));

    }

    private void saveNextWeekMaxPrice(String symbol, Instant now) {
        Instant before2Hour = now.minus(2, HOURS);
        Instant beforeOneWeek = now.minus(7, DAYS);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        cryptoRepository.updateNextWeekMaxPriceBySymbolAndCreatedAtBetween(lastFifteenMinuteMax,
                                                                           symbol,
                                                                           beforeOneWeek.toEpochMilli(),
                                                                           before2Hour.toEpochMilli());
    }

    @Async
    @Time
    <R extends CryptoResult> void saveAll(List<R> cryptoDtos) {
        cryptoDtos.forEach(crypto -> cryptoRepository.save((Crypto) crypto));
    }
}

