package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CryptoJson;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.factory.CryptoFactory;
import com.psw.cta.service.factory.CryptoJsonFactory;
import com.psw.cta.service.factory.StatsFactory;
import com.vaadin.flow.spring.annotation.SpringComponent;
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

@SpringComponent
public class CryptoService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private CryptoFactory cryptoFactory;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CryptoJsonFactory cryptoJsonFactory;

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
        List<Crypto> cryptos = cryptoRepository.findLastDayCryptos(beforeOneDay);
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

    public List<CryptoJson> getActualCryptos() {
        List<Crypto> cryptos = cryptoRepository.findOrderedCryptos();
        Crypto latestCrypto = cryptos.get(0);
        LocalDateTime latestCreatedAt = latestCrypto.getCreatedAt();
        List<Crypto> lastCryptos = cryptoRepository.findLastCryptos(latestCreatedAt);
        return lastCryptos.stream()
                .map(cryptoJsonFactory::create)
                .collect(Collectors.toList());
    }

    public Stats getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.minusDays(1);
        double oneDayStats = getStats(endDate.minusDays(1), endDate);
        double oneWeekStats = getStats(endDate.minusWeeks(1), endDate);
        double oneMonthStats = getStats(endDate.minusMonths(1), endDate);
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats(LocalDateTime startDate, LocalDateTime endDate) {
        List<Crypto> lastDayCryptos = findCryptosBetween(startDate, endDate);
        int size = lastDayCryptos.size();
        long count = lastDayCryptos.stream()
                .filter(crypto -> crypto.getPriceToSell().compareTo(crypto.getNextDayMaxValue()) < 0)
                .count();
        return size > 0 ? (double) count / (double) size * 100 : -1;
    }

    private List<Crypto> findCryptosBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return cryptoRepository.findByCreatedAtBetween(startDate, endDate);
    }
}

