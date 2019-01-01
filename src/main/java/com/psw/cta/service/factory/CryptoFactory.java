package com.psw.cta.service.factory;

import com.psw.cta.entity.Crypto;
import com.psw.cta.service.dto.CryptoDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CryptoFactory {

    public Crypto createCrypto(CryptoDto cryptoDto) {
        Crypto crypto = new Crypto();
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setPriceToSell(cryptoDto.getPriceToSell());
        crypto.setPriceToSellPercentage(cryptoDto.getPriceToSellPercentage());
        crypto.setSumDiffsPerc(cryptoDto.getSumDiffsPerc());
        crypto.setSumDiffsPerc10h(cryptoDto.getSumDiffsPerc10h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight(cryptoDto.getWeight());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }
}
