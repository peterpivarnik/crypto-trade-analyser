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
        crypto.setPriceToSell2h(cryptoDto.getPriceToSell2h());
        crypto.setPriceToSell5h(cryptoDto.getPriceToSell5h());
        crypto.setPriceToSell10h(cryptoDto.getPriceToSell10h());
        crypto.setPriceToSell24h(cryptoDto.getPriceToSell24h());
        crypto.setPriceToSellPercentage2h(cryptoDto.getPriceToSellPercentage2h());
        crypto.setPriceToSellPercentage5h(cryptoDto.getPriceToSellPercentage5h());
        crypto.setPriceToSellPercentage10h(cryptoDto.getPriceToSellPercentage10h());
        crypto.setPriceToSellPercentage24h(cryptoDto.getPriceToSellPercentage24h());
        crypto.setSumDiffsPerc2h(cryptoDto.getSumDiffsPerc2h());
        crypto.setSumDiffsPerc5h(cryptoDto.getSumDiffsPerc5h());
        crypto.setSumDiffsPerc10h(cryptoDto.getSumDiffsPerc10h());
        crypto.setSumDiffsPerc24h(cryptoDto.getSumDiffsPerc24h());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight2h(cryptoDto.getWeight2h());
        crypto.setWeight5h(cryptoDto.getWeight5h());
        crypto.setWeight10h(cryptoDto.getWeight10h());
        crypto.setWeight24h(cryptoDto.getWeight24h());
        crypto.setNextDayMaxPrice(BigDecimal.ZERO);
        return crypto;
    }
}
