package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.CompleteStatsImpl;
import com.psw.cta.rest.dto.StatsImpl;
import com.psw.cta.service.dto.ActualCryptos;
import com.psw.cta.service.dto.AverageProfit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static java.util.Collections.emptyList;

@Service
public class CacheService {

    private CryptoService cryptoService;

    private CompleteStats completeStats = new CompleteStatsImpl(new StatsImpl(0, 0, 0));
    private ActualCryptos actualCryptos = new ActualCryptos(emptyList());

    public CacheService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void updateCompleteStats() {
        this.completeStats = cryptoService.getStats();
    }

    @Time
    public CompleteStats getCompleteStats() {
        return completeStats;
    }

    public ActualCryptos getCryptos() {
        return actualCryptos;
    }

    void setActualCryptos(ActualCryptos actualCryptos) {
        this.actualCryptos = actualCryptos;
    }
}
