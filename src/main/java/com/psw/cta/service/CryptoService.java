package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.repository.CryptoRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.psw.cta.service.dto.BinanceInterval.ONE_MIN;

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
    private CryptoFactory cryptoFactory;

    @Autowired
    private BinanceService binanceService;

    @Time
    @Transactional
    public ActualCryptos getActualCryptos() {
        Instant now = Instant.now();
        Instant beforeMinute = now.minus(1, ChronoUnit.MINUTES);

        List<CryptoResult> cryptos1H = cryptoRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                                               now.toEpochMilli(),
                                                                               sortByWeightAsc())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());

        return new ActualCryptos(cryptos1H);
    }

    private Sort sortByWeightAsc() {
        return new Sort(Sort.Direction.DESC, "weight");
    }

    @Transactional
    @Time
    public CompleteStats getStats() {
        Instant endDate = Instant.now();
        Instant beforeOneDay = endDate.minus(1, ChronoUnit.DAYS);
        Stats stats1H = getCompleteStats(beforeOneDay);
        return completeStatsFactory.createCompleteStats(stats1H);
    }

    private Stats getCompleteStats(Instant endDate) {
        double oneDayStats = getStats(endDate.minus(1, ChronoUnit.DAYS).toEpochMilli(),
                                      endDate.toEpochMilli());
        double oneWeekStats = getStats(endDate.minus(7, ChronoUnit.DAYS).toEpochMilli(),
                                       endDate.toEpochMilli());
        double oneMonthStats = getStats(endDate.minus(30, ChronoUnit.DAYS).toEpochMilli(),
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
    public AverageProfit getAverageProfit() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant beforeTwoDays = now.minus(2, ChronoUnit.DAYS);
        Long startDate = beforeTwoDays.toEpochMilli();
        Long endDate = beforeOneDay.toEpochMilli();
        Optional<Double> average1H = cryptoRepository.findAveragePriceToSellPercentage(startDate, endDate);
        return new AverageProfit(new BigDecimal(average1H.orElse(0d)));
    }

    @Time
    @Scheduled(cron = "0 */15 * * * ?")
    public void updateAll() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        int sum = cryptoRepository.findUniqueSymbols(beforeOneDay.toEpochMilli()).stream()
                .mapToInt(symbol -> saveData1H(symbol, now))
                .sum();

        log.info("Total updates: " + sum);
    }

    private int saveData1H(String symbol, Instant now) {
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant before15Min = now.minus(15, ChronoUnit.MINUTES);
        List<BinanceCandlestick> klines = binanceService.klines(new BinanceSymbol(symbol), ONE_MIN, 15);
        BigDecimal lastFifteenMinuteMax = klines.stream()
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        return cryptoRepository.update(lastFifteenMinuteMax,
                                       symbol,
                                       beforeOneDay.toEpochMilli(),
                                       before15Min.toEpochMilli());
    }

    @Async
    @Time
    void saveAll(List<CryptoDto> cryptoDtos) {
        Long now = Instant.now().toEpochMilli();
        cryptoDtos.stream()
                .map(cryptoDto -> cryptoFactory.createCrypto(cryptoDto))
                .filter(crypto -> crypto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                .filter(crypto -> crypto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                .peek(crypto -> crypto.setCreatedAt(now))
                .peek(crypto -> crypto.setId(null))
                .forEach(crypto -> cryptoRepository.save(crypto));
    }
}

