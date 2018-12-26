package com.psw.cta.rest;

import com.psw.cta.entity.CryptoResult;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.service.CacheService;
import com.psw.cta.service.dto.ActualCryptos;
import com.psw.cta.service.dto.AverageProfit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CryptoRestController {

    @Autowired
    private CacheService cacheService;

    @RequestMapping(value = "/crypto", method = RequestMethod.GET, produces = "application/json")
    public ActualCryptos getCrypto() {
        return cacheService.getCryptos();
    }

    @RequestMapping(value = "/stats", method = RequestMethod.GET, produces = "application/json")
    public CompleteStats getStats() {
        return cacheService.getCompleteStats();
    }

    @RequestMapping(value = "/profit", method = RequestMethod.GET, produces = "application/json")
    public AverageProfit getProfit() {
        return cacheService.getAverageProfit();
    }
}

