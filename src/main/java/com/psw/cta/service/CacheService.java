package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.service.dto.ActualCryptos;
import com.psw.cta.service.dto.AverageProfit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static java.util.Collections.emptyList;

@Service
public class CacheService {

    private CompleteStats completeStats;
    private AverageProfit averageProfit;
    private ActualCryptos actualCryptos = new ActualCryptos(emptyList());

    @Autowired
    private CryptoService cryptoService;

    @Scheduled(cron = "0 */15 * * * ?")
    public void updateCompleteStats() {
        this.completeStats = cryptoService.getStats();
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void updateAverageProfit() {
        this.averageProfit = cryptoService.getAverageProfit();
    }

    @Time
    public CompleteStats getCompleteStats() {
        return completeStats;
    }

    @Time
    public AverageProfit getAverageProfit() {
        return averageProfit;
    }

    public ActualCryptos getCryptos() {
        return actualCryptos;
    }

    public void setActualCryptos(ActualCryptos actualCryptos) {
        this.actualCryptos = actualCryptos;
    }
}
