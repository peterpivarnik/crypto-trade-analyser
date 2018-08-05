package com.psw.cta.rest;

import com.psw.cta.rest.dto.CryptoJson;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CryptoRestController {

    @Autowired
    private CryptoService cryptoService;

    @RequestMapping(value = "/crypto", method = RequestMethod.GET, produces = "application/json")
    public List<CryptoJson> getCrypto() {
        return cryptoService.getActualCryptos();
    }

    @RequestMapping(value = "/stats", method = RequestMethod.GET, produces = "application/json")
    public Stats getStats() {
        return cryptoService.getStats();
    }
}
