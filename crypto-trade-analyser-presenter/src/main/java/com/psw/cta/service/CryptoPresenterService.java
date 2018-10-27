package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.AverageProfit;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.StatsFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CryptoPresenterService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CompleteStatsFactory completeStatsFactory;

    @Transactional
    public List<CryptoResult> getActualCryptos() {
        List<Crypto> cryptos = cryptoRepository.findOrderedCryptos();
        if (cryptos.size() == 0) {
            return new ArrayList<>();
        }
        Crypto latestCrypto = cryptos.get(0);
        LocalDateTime latestCreatedAt = latestCrypto.getCreatedAt();
        List<Crypto> lastCryptos = cryptoRepository.findLastCryptos(latestCreatedAt);
        return lastCryptos.stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CompleteStats getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.minusDays(1);
        Stats stats2H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage2h);
        Stats stats4H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage5h);
        Stats stats10H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage10h);
        Stats stats24H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage24h);
        return completeStatsFactory.createCompleteStats(stats2H, stats4H, stats10H, stats24H);
    }

    private Stats getCompleteStats(LocalDateTime endDate, Function<Crypto, BigDecimal> function) {
        double oneDayStats = getStats(endDate.minusDays(1), endDate, function);
        double oneWeekStats = getStats(endDate.minusWeeks(1), endDate, function);
        double oneMonthStats = getStats(endDate.minusMonths(1), endDate, function);
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats(LocalDateTime startDate, LocalDateTime endDate, Function<Crypto, BigDecimal> function) {
        List<Crypto> lastDayCryptos = cryptoRepository.findByCreatedAtBetween(startDate, endDate);
        int size = lastDayCryptos.size();
        long count = lastDayCryptos.stream()
                .filter(crypto -> function.apply(crypto).compareTo(crypto.getNextDayMaxPrice()) < 0)
                .count();
        return size > 0 ? (double) count / (double) size * 100 : -1;
    }

    public AverageProfit getAverageProfit() {
        BigDecimal average2H = getAverage(CryptoType.TYPE_2H, Crypto::getPriceToSellPercentage2h);
        BigDecimal average5H = getAverage(CryptoType.TYPE_5H, Crypto::getPriceToSellPercentage5h);
        BigDecimal average10H = getAverage(CryptoType.TYPE_10H, Crypto::getPriceToSellPercentage10h);
        BigDecimal average24H = getAverage(CryptoType.TYPE_24H, Crypto::getPriceToSellPercentage24h);
        return new AverageProfit(average2H, average5H, average10H, average24H);
    }

    private BigDecimal getAverage(CryptoType byCryptoType, Function<Crypto, BigDecimal> function) {
        List<Crypto> cryptos = cryptoRepository.findByCryptoType(byCryptoType);
        int size = cryptos.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        return cryptos.stream()
                .map(function)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(size), 8, BigDecimal.ROUND_UP);
    }
}

