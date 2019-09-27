package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.Statistic;
import com.psw.cta.entity.Statistic2Day;
import com.psw.cta.entity.StatisticWeek;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.repository.Statistic2DayRepository;
import com.psw.cta.repository.StatisticRepository;
import com.psw.cta.repository.StatisticWeekRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class StatisticService {

    @Autowired
    private StatisticRepository statisticRepository;

    @Autowired
    private Statistic2DayRepository statistic2DayRepository;

    @Autowired
    private StatisticWeekRepository statisticWeekRepository;

    @Autowired
    private CryptoRepository cryptoRepository;

    @Time
    @Scheduled(cron = "0 */5 * * * ?")
    public void calculateStatistics() {
        calculateOneDayStatistic();
        calculateTwoDayStatistic();
        calculateOneWeekStatistic();
    }

    private void calculateOneDayStatistic() {
        Instant now = Instant.now();
        long from = now.minus(Period.ofDays(2)).toEpochMilli();
        Long maxCreatedAt = statisticRepository.findMaxCreatedAt();
        Instant to = now.minus(1, ChronoUnit.DAYS);
        cryptoRepository.findUniqueCreatedAtByCreatedAtBetween(maxCreatedAt != null ? maxCreatedAt : from,
                                                               to.toEpochMilli())
                .forEach(createdAt -> {
                    List<Crypto> cryptos = cryptoRepository.findByCreatedAt(createdAt);
                    long all = cryptos.size();
                    long valid = cryptos.stream()
                            .filter(crypto -> crypto.getNextDayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                            .count();
                    BigDecimal successRate = new BigDecimal(((double) valid) / all * 100);
                    Statistic statistic = new Statistic();
                    statistic.setCreatedAt(createdAt);
                    statistic.setCreatedAtDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt),
                                                                       ZoneId.of("Europe/Vienna")));
                    statistic.setSuccessRate(successRate);
                    statisticRepository.save(statistic);
                });
    }

    private void calculateTwoDayStatistic() {
        Instant now = Instant.now();
        long from = now.minus(Period.ofDays(3)).toEpochMilli();
        Long maxCreatedAt = statisticRepository.findMaxCreatedAt();
        Instant to = now.minus(2, ChronoUnit.DAYS);
        cryptoRepository.findUniqueCreatedAtByCreatedAtBetween(maxCreatedAt != null ? maxCreatedAt : from,
                                                               to.toEpochMilli())
                .forEach(createdAt -> {
                    List<Crypto> cryptos = cryptoRepository.findByCreatedAt(createdAt);
                    long all = cryptos.size();
                    long valid = cryptos.stream()
                            .filter(crypto -> crypto.getNext2DayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                            .count();
                    BigDecimal successRate = new BigDecimal(((double) valid) / all * 100);
                    Statistic2Day statistic2Day = new Statistic2Day();
                    statistic2Day.setCreatedAt(createdAt);
                    statistic2Day.setCreatedAtDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt),
                                                                           ZoneId.of("Europe/Vienna")));
                    statistic2Day.setSuccessRate(successRate);
                    statistic2DayRepository.save(statistic2Day);
                });
    }

    private void calculateOneWeekStatistic() {
        Instant now = Instant.now();
        long from = now.minus(Period.ofDays(8)).toEpochMilli();
        Long maxCreatedAt = statisticRepository.findMaxCreatedAt();
        Instant to = now.minus(7, ChronoUnit.DAYS);
        cryptoRepository.findUniqueCreatedAtByCreatedAtBetween(maxCreatedAt != null ? maxCreatedAt : from,
                                                               to.toEpochMilli())
                .forEach(createdAt -> {
                    List<Crypto> cryptos = cryptoRepository.findByCreatedAt(createdAt);
                    long all = cryptos.size();
                    long valid = cryptos.stream()
                            .filter(crypto -> crypto.getNextWeekMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                            .count();
                    BigDecimal successRate = new BigDecimal(((double) valid) / all * 100);
                    StatisticWeek statisticWeek = new StatisticWeek();
                    statisticWeek.setCreatedAt(createdAt);
                    statisticWeek.setCreatedAtDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt),
                                                                       ZoneId.of("Europe/Vienna")));
                    statisticWeek.setSuccessRate(successRate);
                    statisticWeekRepository.save(statisticWeek);
                });
    }


}
