package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.service.dto.CryptoDto;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceSymbol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static com.webcerebrium.binance.datatype.BinanceInterval.FIFTEEN_MIN;

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
        cryptoRepository.flush();
    }

    @Async
    @Transactional
    void updateAll(BinanceApi api) {
        LocalDateTime beforeOneDay = LocalDateTime.now().minusDays(1);
        List<Crypto> cryptos = cryptoRepository.findNewest(beforeOneDay);
        cryptos.parallelStream()
                .collect(Collectors.groupingBy(Crypto::getSymbol))
                .forEach((symbol, grouppedCryptos) -> updateGrouppedCryptos(api, symbol, grouppedCryptos));
    }

    private void updateGrouppedCryptos(BinanceApi api, String symbol, List<Crypto> grouppedCryptos) {
        List<BinanceCandlestick> klines;
        try {
            klines = api.klines(new BinanceSymbol(symbol), FIFTEEN_MIN, 96, null);
        } catch (BinanceApiException e) {
            throw new RuntimeException("wrong symbol", e);
        }
        grouppedCryptos.parallelStream()
                .forEach(crypto -> updateCrypto(klines, crypto));
    }

    private void updateCrypto(List<BinanceCandlestick> klines, Crypto crypto) {
        long createdAtMillis = crypto.getCreatedAt()
                .atZone(ZoneId.of("CET"))
                .toInstant()
                .toEpochMilli();

        BigDecimal max = klines.stream()
                .filter(kline -> createdAtMillis < kline.getOpenTime())
                .map(BinanceCandlestick::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (crypto.getNextDayMaxValue().compareTo(max) < 0) {
            crypto.setNextDayMaxValue(max);
            cryptoRepository.save(crypto);
        }
    }


}

