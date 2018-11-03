package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.service.dto.AverageProfit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class CacheService {

    private CompleteStats completeStats;
    private AverageProfit averageProfit;

    @Autowired
    private CryptoService cryptoService;

    @PostConstruct
    public void init() {
        updateAverageProfit();
        updateCompleteStats();
    }

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

    public List<CryptoResult> getCryptos() {
        return cryptoService.getActualCryptos();
    }
}
