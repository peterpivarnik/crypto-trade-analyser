package com.psw.cta.rest;

import com.psw.cta.rest.dto.SuccessRate;
import com.psw.cta.service.CacheService;
import com.psw.cta.service.dto.ActualCryptos;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CryptoRestController {

    private CacheService cacheService;

    public CryptoRestController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @RequestMapping(value = "/crypto", method = RequestMethod.GET, produces = "application/json")
    public ActualCryptos getCrypto() {
        return cacheService.getCryptos();
    }

    @RequestMapping(value = "/stats", method = RequestMethod.GET, produces = "application/json")
    public SuccessRate getSuccessRate() {
        return cacheService.getSuccessRate();
    }
}

