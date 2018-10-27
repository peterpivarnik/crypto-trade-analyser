package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.aspect.Time;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.factory.CryptoDtoFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CryptoCollectorService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private CryptoDtoFactory cryptoDtoFactory;

    @Async
    @Time
    @Transactional
    void saveAll(List<CryptoDto> cryptoDtos) {
        List<Crypto> cryptos = cryptoDtos.stream()
                .map(cryptoDto -> cryptoDtoFactory.createCrypto(cryptoDto))
                .collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        save(cryptos, Crypto::getPriceToSellPercentage2h, CryptoType.TYPE_2H, now);
        save(cryptos, Crypto::getPriceToSellPercentage5h, CryptoType.TYPE_5H, now);
        save(cryptos, Crypto::getPriceToSellPercentage10h, CryptoType.TYPE_10H, now);
        save(cryptos, Crypto::getPriceToSellPercentage24h, CryptoType.TYPE_24H, now);
    }

    private void save(List<Crypto> cryptos,
                      Function<Crypto, BigDecimal> function,
                      CryptoType cryptoType,
                      LocalDateTime now) {
        List<Crypto> updatedCryptos = cryptos.stream()
                .filter(crypto -> function.apply(crypto).compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto -> crypto.setCryptoType(cryptoType))
                .peek(crypto -> crypto.setCreatedAt(now))
                .peek(crypto -> crypto.setId(null))
                .collect(Collectors.toList());
        cryptoRepository.saveAll(updatedCryptos);
        cryptoRepository.flush();
    }
}

