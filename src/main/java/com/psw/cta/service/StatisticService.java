package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.Statistic;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.repository.StatisticRepository;
import com.psw.cta.service.factory.StatisticFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class StatisticService {

    @Autowired
    private StatisticRepository statisticRepository;

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private StatisticFactory statisticFactory;

    @Time
    @Scheduled(cron = "0 */5 * * * ?")
    public void calculateStatistics() {
        Instant now = Instant.now();
        Instant to = now.minus(1, ChronoUnit.DAYS);
        Long from = 1L;
        Long maxCreatedAt = statisticRepository.findMaxCreatedAt();
        List<Long> uniqueCreatedAt = cryptoRepository.findUniqueCreatedAt(maxCreatedAt != null ? maxCreatedAt : from,
                                                                          to.toEpochMilli());
        uniqueCreatedAt.forEach(this::saveStatistic);
    }

    private void saveStatistic(Long createdAt) {
        List<Crypto> cryptos1H = cryptoRepository.findByCreatedAt(createdAt);
        int all1H = cryptos1H.size();
        long valid1H = cryptos1H.stream()
                .filter(crypto -> crypto.getNextDayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                .count();
        Statistic statistic = statisticFactory.create(createdAt, new BigDecimal(((double) valid1H) / all1H * 100));
        statisticRepository.save(statistic);
    }
}
