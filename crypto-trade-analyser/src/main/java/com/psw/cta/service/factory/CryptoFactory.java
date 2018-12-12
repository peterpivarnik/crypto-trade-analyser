package com.psw.cta.service.factory;

import com.psw.cta.entity.Crypto1H;
import com.psw.cta.entity.Crypto2H;
import com.psw.cta.entity.Crypto5H;
import org.springframework.stereotype.Component;
import com.psw.cta.service.dto.CryptoDto;

import java.math.BigDecimal;

@Component
public class CryptoFactory {

    public Crypto1H createCrypto1H(CryptoDto cryptoDto) {
        Crypto1H crypto = new Crypto1H();
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setPriceToSell(cryptoDto.getPriceToSell1h());
        crypto.setPriceToSellPercentage(cryptoDto.getPriceToSellPercentage1h());
        crypto.setSumDiffsPerc(cryptoDto.getSumDiffsPerc1h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight(cryptoDto.getWeight1h());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }

    public Crypto2H createCrypto2H(CryptoDto cryptoDto) {
        Crypto2H crypto = new Crypto2H();
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setPriceToSell(cryptoDto.getPriceToSell2h());
        crypto.setPriceToSellPercentage(cryptoDto.getPriceToSellPercentage2h());
        crypto.setSumDiffsPerc(cryptoDto.getSumDiffsPerc2h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight(cryptoDto.getWeight2h());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }

    public Crypto5H createCrypto5H(CryptoDto cryptoDto) {
        Crypto5H crypto = new Crypto5H();
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setPriceToSell(cryptoDto.getPriceToSell5h());
        crypto.setPriceToSellPercentage(cryptoDto.getPriceToSellPercentage5h());
        crypto.setSumDiffsPerc(cryptoDto.getSumDiffsPerc5h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight(cryptoDto.getWeight5h());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }
}
