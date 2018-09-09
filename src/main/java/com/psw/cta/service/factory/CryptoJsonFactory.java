package com.psw.cta.service.factory;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.rest.dto.CryptoJson;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CryptoJsonFactory {

    public CryptoJson create(Crypto crypto) {
        CryptoJson cryptoJson = new CryptoJson();
        cryptoJson.setDate(crypto.getCreatedAt());
        cryptoJson.setSymbol(crypto.getSymbol());
        cryptoJson.setCurrentPrice(crypto.getCurrentPrice());
        cryptoJson.setPriceToSell(crypto.getPriceToSell());
        cryptoJson.setPercentage(crypto.getFifteenMinutesPercentageLoss());
        cryptoJson.setCryptoType(crypto.getCryptoType());
        return cryptoJson;
    }

    public CryptoJson create(CryptoType key, List<Crypto> value) {


    }
}
