package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.service.dto.CryptoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
class CryptoService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private CryptoFactory cryptoFactory;

    @Async
    @Transactional
    void saveAll(List<CryptoDto> cryptoDtos) {
        final List<Crypto> cryptos = cryptoDtos.stream()
                .map(cryptoDto -> cryptoFactory.createCrypto(cryptoDto))
                .collect(Collectors.toList());
        cryptoRepository.saveAll(cryptos);
    }
}
