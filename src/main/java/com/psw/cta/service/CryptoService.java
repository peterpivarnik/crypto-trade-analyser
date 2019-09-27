package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.ActualCryptos;
import com.psw.cta.service.dto.AverageProfit;
import com.psw.cta.service.dto.BinanceCandlestick;
import com.psw.cta.service.dto.BinanceSymbol;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.StatsFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.psw.cta.service.dto.BinanceInterval.ONE_MIN;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;

@Component
@Slf4j
public class CryptoService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CompleteStatsFactory completeStatsFactory;

    @Autowired
    private BinanceService binanceService;

    @Time
    @Transactional
    public ActualCryptos getActualCryptos() {
        Instant now = Instant.now();
        Instant beforeMinute = now.minus(1, MINUTES);

        List<CryptoResult> cryptos1H = cryptoRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                                               now.toEpochMilli(),
                                                                               sortByPriceToSellPercentage())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());

        return new ActualCryptos(cryptos1H);
    }

    private Sort sortByPriceToSellPercentage() {
        return new Sort(Sort.Direction.DESC, "priceToSellPercentage");
    }

    @Transactional
    @Time
    CompleteStats getStats() {
        Instant endDate = Instant.now();
        Instant beforeOneDay = endDate.minus(1, DAYS);
        Stats stats1H = getCompleteStats(beforeOneDay);
        return completeStatsFactory.createCompleteStats(stats1H);
    }

    private Stats getCompleteStats(Instant endDate) {
        double oneDayStats = getStats(endDate.minus(1, DAYS).toEpochMilli(),
                                      endDate.toEpochMilli());
        double oneWeekStats = getStats(endDate.minus(7, DAYS).toEpochMilli(),
                                       endDate.toEpochMilli());
        double oneMonthStats = getStats(endDate.minus(30, DAYS).toEpochMilli(),
                                        endDate.toEpochMilli());
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats(Long startDate, Long endDate) {
        double validStats = cryptoRepository.findValidStats1H(startDate, endDate);
        double allStats = cryptoRepository.findAllStats(startDate, endDate);
        if (allStats == 0) {
            return 0d;
        }
        return validStats / allStats * 100;
    }

    @Time
    AverageProfit getAverageProfit() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, DAYS);
        Instant beforeTwoDays = now.minus(2, DAYS);
        Long startDate = beforeTwoDays.toEpochMilli();
        Long endDate = beforeOneDay.toEpochMilli();
        Optional<Double> average1H = cryptoRepository.findAveragePriceToSellPercentage(startDate, endDate);
        return new AverageProfit(new BigDecimal(average1H.orElse(0d)));
    }

    @Time
    @Scheduled(cron = "0 */15 * * * ?")
    public void updateAll() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, DAYS);
        cryptoRepository.findUniqueSymbols(beforeOneDay.toEpochMilli())
                .forEach(symbol -> saveData1H(symbol, now));

    }

    private void saveData1H(String symbol, Instant now) {
        Instant beforeOneDay = now.minus(1, DAYS);
        Instant before15Min = now.minus(15, MINUTES);
        Instant beforeWeek = now.minus(2, DAYS);
        Instant beforeTwoDays = now.minus(7, DAYS);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        cryptoRepository.updateNextDayMaxPrice(lastFifteenMinuteMax,
                                               symbol,
                                               beforeOneDay.toEpochMilli(),
                                               before15Min.toEpochMilli());

        cryptoRepository.updateNext2DayMaxPrice(lastFifteenMinuteMax,
                                                symbol,
                                                beforeTwoDays.toEpochMilli(),
                                                before15Min.toEpochMilli());

        cryptoRepository.updateNextWeekMaxPrice(lastFifteenMinuteMax,
                                                symbol,
                                                beforeWeek.toEpochMilli(),
                                                before15Min.toEpochMilli());
    }

    @Async
    @Time
    <R extends CryptoResult> void saveAll(List<R> cryptoDtos) {
        cryptoDtos.forEach(crypto -> cryptoRepository.save((Crypto) crypto));
    }
}

