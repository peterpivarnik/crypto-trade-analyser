package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.rest.dto.ImmutableSuccessRate;
import com.psw.cta.rest.dto.SuccessRate;
import com.psw.cta.service.dto.ActualCryptos;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static java.util.Collections.emptyList;

@Service
public class CacheService {

    private CryptoService cryptoService;

    private SuccessRate successRate = ImmutableSuccessRate.builder()
            .oneDaySuccessRate(0)
            .twoDaysSuccessRate(0)
            .oneWeekSuccessRate(0)
            .build();
    private ActualCryptos actualCryptos = new ActualCryptos(emptyList());

    public CacheService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void updateSuccessRate() {
        this.successRate = cryptoService.getSuccessRate();
    }

    @Time
    public SuccessRate getSuccessRate() {
        return successRate;
    }

    public ActualCryptos getCryptos() {
        return actualCryptos;
    }

    void setActualCryptos(ActualCryptos actualCryptos) {
        this.actualCryptos = actualCryptos;
    }
}
