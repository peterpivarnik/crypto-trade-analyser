package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.repository.Crypto1HRepository;
import com.psw.cta.repository.Crypto2HRepository;
import com.psw.cta.repository.Crypto5HRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.*;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.CryptoFactory;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.psw.cta.entity.CryptoType.*;
import static com.psw.cta.service.dto.BinanceInterval.ONE_MIN;

@Component
@Slf4j
public class CryptoService {

    @Autowired
    private Crypto1HRepository crypto1HRepository;

    @Autowired
    private Crypto2HRepository crypto2HRepository;

    @Autowired
    private Crypto5HRepository crypto5HRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CompleteStatsFactory completeStatsFactory;

    @Autowired
    private CryptoFactory cryptoFactory;

    @Autowired
    private BinanceService binanceService;

    @Time
    @Transactional
    public ActualCryptos getActualCryptos() {
        Instant now = Instant.now();
        Instant beforeMinute = now.minus(1, ChronoUnit.MINUTES);

        List<CryptoResult> cryptos1H = crypto1HRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                                                 now.toEpochMilli(),
                                                                                 sortByWeightAsc())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());

        List<CryptoResult> cryptos2H = crypto2HRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                                                 now.toEpochMilli(),
                                                                                 sortByWeightAsc())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());

        List<CryptoResult> cryptos5H = crypto5HRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                                                 now.toEpochMilli(),
                                                                                 sortByWeightAsc())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());
        return new ActualCryptos(cryptos1H, cryptos2H, cryptos5H);
    }

    private Sort sortByWeightAsc() {
        return new Sort(Sort.Direction.DESC, "weight");
    }

    @Transactional
    @Time
    public CompleteStats getStats() {
        Instant endDate = Instant.now();
        Instant beforeOneDay = endDate.minus(1, ChronoUnit.DAYS);
        Stats stats1H = getCompleteStats1h(beforeOneDay);
        Stats stats2H = getCompleteStats2h(beforeOneDay);
        Stats stats4H = getCompleteStats5h(beforeOneDay);
        return completeStatsFactory.createCompleteStats(stats1H, stats2H, stats4H);
    }

    private Stats getCompleteStats1h(Instant endDate) {
        double oneDayStats = getStats1h(endDate.minus(1, ChronoUnit.DAYS).toEpochMilli(),
                                        endDate.toEpochMilli());
        double oneWeekStats = getStats1h(endDate.minus(7, ChronoUnit.DAYS).toEpochMilli(),
                                         endDate.toEpochMilli());
        double oneMonthStats = getStats1h(endDate.minus(30, ChronoUnit.DAYS).toEpochMilli(),
                                          endDate.toEpochMilli());
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private Stats getCompleteStats2h(Instant endDate) {
        double oneDayStats = getStats2h(endDate.minus(1, ChronoUnit.DAYS).toEpochMilli(),
                                        endDate.toEpochMilli());
        double oneWeekStats = getStats2h(endDate.minus(7, ChronoUnit.DAYS).toEpochMilli(),
                                         endDate.toEpochMilli());
        double oneMonthStats = getStats2h(endDate.minus(30, ChronoUnit.DAYS).toEpochMilli(),
                                          endDate.toEpochMilli());
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private Stats getCompleteStats5h(Instant endDate) {
        double oneDayStats = getStats5h(endDate.minus(1, ChronoUnit.DAYS).toEpochMilli(),
                                        endDate.toEpochMilli());
        double oneWeekStats = getStats5h(endDate.minus(7, ChronoUnit.DAYS).toEpochMilli(),
                                         endDate.toEpochMilli());
        double oneMonthStats = getStats5h(endDate.minus(30, ChronoUnit.DAYS).toEpochMilli(),
                                          endDate.toEpochMilli());
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats1h(Long startDate, Long endDate) {
        double validStats = crypto1HRepository.findValidStats1H(TYPE_1H, startDate, endDate);
        double allStats = crypto1HRepository.findAllStats(TYPE_1H, startDate, endDate);
        if (allStats == 0) {
            return 0d;
        }
        return validStats / allStats * 100;
    }

    private double getStats2h(Long startDate, Long endDate) {
        double validStats = crypto2HRepository.findValidStats2H(TYPE_2H, startDate, endDate);
        double allStats = crypto2HRepository.findAllStats(TYPE_2H, startDate, endDate);
        if (allStats == 0) {
            return 0d;
        }
        return validStats / allStats * 100;
    }

    private double getStats5h(Long startDate, Long endDate) {
        double validStats = crypto5HRepository.findValidStats5H(TYPE_5H, startDate, endDate);
        double allStats = crypto5HRepository.findAllStats(TYPE_5H, startDate, endDate);
        if (allStats == 0) {
            return 0d;
        }
        return validStats / allStats * 100;
    }

    @Time
    public AverageProfit getAverageProfit() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant beforeTwoDays = now.minus(2, ChronoUnit.DAYS);
        Long startDate = beforeTwoDays.toEpochMilli();
        Long endDate = beforeOneDay.toEpochMilli();
        Optional<Double> average1H = crypto1HRepository.findAveragePriceToSellPercentage(TYPE_1H,
                                                                                         startDate,
                                                                                         endDate);
        Optional<Double> average2H = crypto2HRepository.findAveragePriceToSellPercentage(TYPE_2H,
                                                                                         startDate,
                                                                                         endDate);
        Optional<Double> average5H = crypto5HRepository.findAveragePriceToSellPercentage(TYPE_5H,
                                                                                         startDate,
                                                                                         endDate);
        return new AverageProfit(new BigDecimal(average1H.orElse(0d)),
                                 new BigDecimal(average2H.orElse(0d)),
                                 new BigDecimal(average5H.orElse(0d)));
    }

    @Time
    @Scheduled(cron = "0 */5 * * * ?")
    public void updateAll() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        int sum1H = crypto1HRepository.findUniqueSymbols(beforeOneDay.toEpochMilli()).stream()
                .mapToInt(symbol -> saveData1H(symbol, now))
                .sum();

        int sum2H = crypto2HRepository.findUniqueSymbols(beforeOneDay.toEpochMilli()).stream()
                .mapToInt(symbol -> saveData2H(symbol, now))
                .sum();

        int sum5H = crypto5HRepository.findUniqueSymbols(beforeOneDay.toEpochMilli()).stream()
                .mapToInt(symbol -> saveData5H(symbol, now))
                .sum();
        log.info("Total 1H updates: " + sum1H + ", toatal 2H updates: " + sum2H + ", total 5H updates: " + sum5H);
    }

    private int saveData1H(String symbol, Instant now) {
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant before15Min = now.minus(15, ChronoUnit.MINUTES);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return crypto1HRepository.update(lastFifteenMinuteMax, symbol, beforeOneDay.toEpochMilli(), before15Min.toEpochMilli());
    }

    private int saveData2H(String symbol, Instant now) {
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant before15Min = now.minus(15, ChronoUnit.MINUTES);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return crypto2HRepository.update(lastFifteenMinuteMax, symbol, beforeOneDay.toEpochMilli(), before15Min.toEpochMilli());
    }

    private int saveData5H(String symbol, Instant now) {
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant before15Min = now.minus(15, ChronoUnit.MINUTES);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        return crypto5HRepository.update(lastFifteenMinuteMax, symbol, beforeOneDay.toEpochMilli(), before15Min.toEpochMilli());
    }

    @Async
    @Time
    void saveAll(List<CryptoDto> cryptoDtos) {
        Long now = Instant.now().toEpochMilli();
        cryptoDtos.stream()
                .map(cryptoDto -> cryptoFactory.createCrypto1H(cryptoDto))
                .filter(crypto1H -> crypto1H.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto1H -> crypto1H.setCryptoType(TYPE_1H))
                .peek(crypto1H -> crypto1H.setCreatedAt(now))
                .peek(crypto1H -> crypto1H.setId(null))
                .forEach(crypto1H -> crypto1HRepository.save(crypto1H));

        cryptoDtos.stream()
                .map(cryptoDto -> cryptoFactory.createCrypto2H(cryptoDto))
                .filter(crypto2H -> crypto2H.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto2H -> crypto2H.setCryptoType(TYPE_2H))
                .peek(crypto2H -> crypto2H.setCreatedAt(now))
                .peek(crypto2H -> crypto2H.setId(null))
                .forEach(crypto2H -> crypto2HRepository.save(crypto2H));

        cryptoDtos.stream()
                .map(cryptoDto -> cryptoFactory.createCrypto5H(cryptoDto))
                .filter(crypto5H -> crypto5H.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto5H -> crypto5H.setCryptoType(TYPE_5H))
                .peek(crypto5H -> crypto5H.setCreatedAt(now))
                .peek(crypto5H -> crypto5H.setId(null))
                .forEach(crypto5H -> crypto5HRepository.save(crypto5H));
    }
}

