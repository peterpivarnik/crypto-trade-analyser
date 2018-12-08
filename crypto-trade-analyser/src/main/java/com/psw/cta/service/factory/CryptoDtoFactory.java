package com.psw.cta.service.factory;

import com.psw.cta.entity.Crypto;
import org.springframework.stereotype.Component;
import com.psw.cta.service.dto.CryptoDto;

import java.math.BigDecimal;

@Component
public class CryptoDtoFactory {

    public Crypto createCrypto(CryptoDto cryptoDto) {
        Crypto crypto = new Crypto();
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setPriceToSell1h(cryptoDto.getPriceToSell1h());
        crypto.setPriceToSell2h(cryptoDto.getPriceToSell2h());
        crypto.setPriceToSell5h(cryptoDto.getPriceToSell5h());
        crypto.setPriceToSellPercentage1h(cryptoDto.getPriceToSellPercentage1h());
        crypto.setPriceToSellPercentage2h(cryptoDto.getPriceToSellPercentage2h());
        crypto.setPriceToSellPercentage5h(cryptoDto.getPriceToSellPercentage5h());
        crypto.setSumDiffsPerc1h(cryptoDto.getSumDiffsPerc1h());
        crypto.setSumDiffsPerc2h(cryptoDto.getSumDiffsPerc2h());
        crypto.setSumDiffsPerc5h(cryptoDto.getSumDiffsPerc5h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight1h(cryptoDto.getWeight1h());
        crypto.setWeight2h(cryptoDto.getWeight2h());
        crypto.setWeight5h(cryptoDto.getWeight5h());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }
}
