package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto1H;
import com.psw.cta.entity.Crypto2H;
import com.psw.cta.entity.Crypto5H;
import com.psw.cta.entity.Statistic;
import com.psw.cta.repository.Crypto1HRepository;
import com.psw.cta.repository.Crypto2HRepository;
import com.psw.cta.repository.Crypto5HRepository;
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
    private Crypto1HRepository crypto1HRepository;

    @Autowired
    private Crypto2HRepository crypto2HRepository;

    @Autowired
    private Crypto5HRepository crypto5HRepository;

    @Autowired
    private StatisticFactory statisticFactory;

    @Time
    @Scheduled(cron = "0 */5 * * * ?")
    public void calculateStatistics() {
        Instant now = Instant.now();
        Instant to = now.minus(1, ChronoUnit.DAYS);
        Long from = 1L;
        Long maxCreatedAt = statisticRepository.findMaxCreatedAt();
        List<Long> uniqueCreatedAt = crypto1HRepository.findUniqueCreatedAt(maxCreatedAt != null ? maxCreatedAt : from,
                                                                            to.toEpochMilli());
        uniqueCreatedAt.forEach(this::saveStatistic);
    }

    private void saveStatistic(Long createdAt) {
        List<Crypto1H> cryptos1H = crypto1HRepository.findByCreatedAt(createdAt);
        int all1H = cryptos1H.size();
        long valid1H = cryptos1H.stream()
                .filter(crypto -> crypto.getNextDayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                .count();
        List<Crypto2H> cryptos2H = crypto2HRepository.findByCreatedAt(createdAt);
        int all2H = cryptos2H.size();
        long valid2H = cryptos2H.stream()
                .filter(crypto -> crypto.getNextDayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                .count();
        List<Crypto5H> cryptos5H = crypto5HRepository.findByCreatedAt(createdAt);
        int all5H = cryptos5H.size();
        long valid5H = cryptos5H.stream()
                .filter(crypto -> crypto.getNextDayMaxPrice().compareTo(crypto.getPriceToSell()) >= 0)
                .count();
        Statistic statistic = statisticFactory.create(createdAt,
                                                      new BigDecimal(((double) valid1H) / all1H * 100),
                                                      new BigDecimal(((double) valid2H) / all2H * 100),
                                                      new BigDecimal(((double) valid5H) / all5H * 100));
        statisticRepository.save(statistic);
    }
}
