package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.AverageProfit;
import com.psw.cta.service.dto.BinanceCandlestick;
import com.psw.cta.service.dto.BinanceSymbol;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.CryptoDtoFactory;
import com.psw.cta.service.factory.StatsFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.psw.cta.entity.CryptoType.TYPE_2H;
import static com.psw.cta.entity.CryptoType.TYPE_5H;
import static com.psw.cta.service.dto.BinanceInterval.FIFTEEN_MIN;

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
    private CryptoDtoFactory cryptoDtoFactory;

    @Autowired
    private BinanceService binanceService;

    @Time
    @Transactional
    public List<CryptoResult> getActualCryptos() {
        Instant now = Instant.now();
        Instant beforeMinute = now.minus(1, ChronoUnit.MINUTES);
        return cryptoRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                       now.toEpochMilli())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());
    }

    @Transactional
    @Time
    public CompleteStats getStats() {
        Instant endDate = Instant.now();
        Instant beforeOneDay = endDate.minus(1, ChronoUnit.DAYS);
        Stats stats2H = getCompleteStats2h(beforeOneDay);
        Stats stats4H = getCompleteStats5h(beforeOneDay);
        return completeStatsFactory.createCompleteStats(stats2H, stats4H);
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

    private double getStats2h(Long startDate, Long endDate) {
        double validStats = cryptoRepository.findValidStats2H(TYPE_2H, startDate, endDate);
        double allStats = cryptoRepository.findAllStats(TYPE_2H, startDate, endDate);
        return validStats / allStats * 100;
    }

    private double getStats5h(Long startDate, Long endDate) {
        double validStats = cryptoRepository.findValidStats5H(TYPE_5H, startDate, endDate);
        double allStats = cryptoRepository.findAllStats(TYPE_5H, startDate, endDate);
        return validStats / allStats * 100;
    }

    @Time
    public AverageProfit getAverageProfit() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        Instant beforeTwoDays = now.minus(2, ChronoUnit.DAYS);
        Long startDate = beforeTwoDays.toEpochMilli();
        Long endDate = beforeOneDay.toEpochMilli();
        Optional<Double> average2H = cryptoRepository.findAveragePriceToSellPercentage2h(TYPE_2H,
                                                                                         startDate,
                                                                                         endDate);
        Optional<Double> average5H = cryptoRepository.findAveragePriceToSellPercentage5h(TYPE_5H,
                                                                                         startDate,
                                                                                         endDate);
        return new AverageProfit(new BigDecimal(average2H.orElse(0d)), new BigDecimal(average5H.orElse(0d)));
    }

    @Time
    @Scheduled(cron = "0 */15 * * * ?")
    public void updateAll() {
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        int sum = cryptoRepository.findUniqueSymbols(beforeOneDay.toEpochMilli()).stream()
                .mapToInt(symbol -> saveData(symbol, beforeOneDay))
                .sum();
        log.info("Total updates: " + sum);
    }

    private int saveData(String symbol, Instant beforeOneDay) {
        List<BinanceCandlestick> klines;
        try {
            klines = binanceService.klines(new BinanceSymbol(symbol), FIFTEEN_MIN, 1);
        } catch (CryptoTradeAnalyserException e) {
            throw new RuntimeException("Problem during binance klines", e);
        }
        BigDecimal lastFifteenMinuteMax = klines.get(0).getHigh();
        return cryptoRepository.update(lastFifteenMinuteMax, symbol, beforeOneDay.toEpochMilli());
    }

    @Async
    @Time
    void saveAll(List<CryptoDto> cryptoDtos) {
        List<Crypto> cryptos = cryptoDtos.stream()
                .map(cryptoDto -> cryptoDtoFactory.createCrypto(cryptoDto))
                .collect(Collectors.toList());
        Long now = Instant.now().toEpochMilli();
        save(cryptos,
             crypto -> crypto.getPriceToSellPercentage2h().compareTo(new BigDecimal("0.5")) > 0
//                       && crypto.getWeight2h().compareTo(new BigDecimal("10000")) > 0
                ,
             TYPE_2H,
             now);
        save(cryptos,
             crypto -> crypto.getPriceToSellPercentage5h().compareTo(new BigDecimal("0.5")) > 0
//                       && crypto.getWeight5h().compareTo(new BigDecimal("10000")) > 0
                ,
             TYPE_5H,
             now);
    }

    private void save(List<Crypto> cryptos,
                      Function<Crypto, Boolean> function,
                      CryptoType cryptoType,
                      Long now) {
        List<Crypto> filteredCryptos = cryptos.stream()
                .filter(function::apply)
                .peek(crypto -> crypto.setCryptoType(cryptoType))
                .peek(crypto -> crypto.setCreatedAt(now))
                .peek(crypto -> crypto.setId(null))
                .collect(Collectors.toList());
        cryptoRepository.saveAll(filteredCryptos);
    }
}

