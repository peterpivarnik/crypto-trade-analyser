package com.psw.cta.service.factory;

import com.psw.cta.entity.Crypto;
import com.psw.cta.service.dto.CryptoDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class CryptoFactory {

    public Crypto createCrypto(CryptoDto cryptoDto, Long nowMillis, LocalDateTime nowDate) {
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
        crypto.setId(null);
        crypto.setCreatedAt(nowMillis);
        crypto.setCreatedAtDate(nowDate);
        return crypto;
    }
}
