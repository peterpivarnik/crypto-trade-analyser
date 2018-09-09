package com.psw.cta.service;

import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.CryptoJsonFactory;
import com.psw.cta.service.factory.StatsFactory;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceSymbol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.webcerebrium.binance.datatype.BinanceInterval.FIFTEEN_MIN;

@SpringComponent
public class CryptoService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CryptoJsonFactory cryptoJsonFactory;

    @Autowired
    private CompleteStatsFactory completeStatsFactory;

    @Async
    @Transactional
    void saveAll(List<CryptoDto> cryptoDtos) {
        save(cryptoDtos, CryptoDto::getPriceToSellPercentage2h, CryptoType.TYPE_2H);
        save(cryptoDtos, CryptoDto::getPriceToSellPercentage5h, CryptoType.TYPE_5H);
        save(cryptoDtos, CryptoDto::getPriceToSellPercentage10h, CryptoType.TYPE_10H);
        save(cryptoDtos, CryptoDto::getPriceToSellPercentage24h, CryptoType.TYPE24H);
        cryptoRepository.flush();
    }

    private void save(List<CryptoDto> cryptoDtos,
                      Function<CryptoDto, BigDecimal> function,
                      CryptoType cryptoType) {
        List<Crypto> cryptos = cryptoDtos.stream()
                .filter(cryptoDto -> function.apply(cryptoDto).compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto -> crypto.setCryptoType(cryptoType))
                .collect(Collectors.toList());
        cryptoRepository.saveAll(cryptos);
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

    @Transactional
    public List<CryptoResult> getActualCryptos() {
        List<Crypto> cryptos = cryptoRepository.findOrderedCryptos();
        Crypto latestCrypto = cryptos.get(0);
        LocalDateTime latestCreatedAt = latestCrypto.getCreatedAt();
        List<Crypto> lastCryptos = cryptoRepository.findLastCryptos(latestCreatedAt);
        return lastCryptos.stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CompleteStats getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.minusDays(1);
        Stats stats2H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage2h);
        Stats stats4H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage5h);
        Stats stats10H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage10h);
        Stats stats24H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage24h);
        return completeStatsFactory.createCompleteStats(stats2H, stats4H, stats10H, stats24H);
    }

    private Stats getCompleteStats(LocalDateTime endDate, Function<Crypto, BigDecimal> function) {
        double oneDayStats = getStats(endDate.minusDays(1), endDate, function);
        double oneWeekStats = getStats(endDate.minusWeeks(1), endDate, function);
        double oneMonthStats = getStats(endDate.minusMonths(1), endDate, function);
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats(LocalDateTime startDate, LocalDateTime endDate, Function<Crypto, BigDecimal> function) {
        List<Crypto> lastDayCryptos = cryptoRepository.findByCreatedAtBetween(startDate, endDate);
        int size = lastDayCryptos.size();
        long count = lastDayCryptos.stream()
                .filter(crypto -> function.apply(crypto).compareTo(crypto.getNextDayMaxValue()) < 0)
                .count();
        return size > 0 ? (double) count / (double) size * 100 : -1;
    }

}

