package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.service.dto.CryptoDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
class CryptoFactory {
    Crypto createCrypto(CryptoDto cryptoDto) {
        Crypto crypto = new Crypto();
        crypto.setCreatedAt(LocalDateTime.now());
        crypto.setCurrentPrice(cryptoDto.getCurrentPrice());
        crypto.setFifteenMinutesMaxToCurrentDifferent(cryptoDto.getFifteenMinutesMaxToCurrentDifferent());
        crypto.setFifteenMinutesPercentageLoss(cryptoDto.getFifteenMinutesPercentageLoss());
        crypto.setLastThreeDaysAveragePrice(cryptoDto.getLastThreeDaysAveragePrice());
        crypto.setLastThreeDaysMaxMinDiffPercent(cryptoDto.getLastThreeDaysMaxMinDiffPercent());
        crypto.setLastThreeDaysMaxPrice(cryptoDto.getLastThreeDaysMaxPrice());
        crypto.setLastThreeDaysMinPrice(cryptoDto.getLastThreeDaysMinPrice());
        crypto.setPriceToSell(cryptoDto.getPriceToSell());
        crypto.setRatio(cryptoDto.getRatio());
        crypto.setSymbol(cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol());
        crypto.setVolume(cryptoDto.getVolume());
        crypto.setWeight(cryptoDto.getWeight());
        return crypto;
    }
}
