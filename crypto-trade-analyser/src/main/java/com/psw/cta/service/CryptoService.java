package com.psw.cta.service;

import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.repository.CryptoRepository;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.dto.AverageProfit;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.factory.CompleteStatsFactory;
import com.psw.cta.service.factory.CryptoDtoFactory;
import com.psw.cta.service.factory.StatsFactory;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceSymbol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.webcerebrium.binance.datatype.BinanceInterval.ONE_DAY;

@Component
public class CryptoService {

    @Autowired
    private CryptoRepository cryptoRepository;

    @Autowired
    private StatsFactory statsFactory;

    @Autowired
    private CompleteStatsFactory completeStatsFactory;

    @Autowired
    private CryptoDtoFactory cryptoDtoFactory;

    @Transactional
    public List<CryptoResult> getActualCryptos() {
        Instant now = Instant.now();
        Instant beforeMinute = now.minus(1, ChronoUnit.MINUTES);
        return cryptoRepository.findByCreatedAtBetween(beforeMinute.toEpochMilli(),
                                                       now.toEpochMilli())
                .stream()
                .map(crypto -> (CryptoResult) crypto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CompleteStats getStats() {
        Instant endDate = Instant.now();
        Stats stats2H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage2h);
        Stats stats4H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage5h);
        Stats stats10H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage10h);
        Stats stats24H = getCompleteStats(endDate, Crypto::getPriceToSellPercentage24h);
        return completeStatsFactory.createCompleteStats(stats2H, stats4H, stats10H, stats24H);
    }

    private Stats getCompleteStats(Instant endDate, Function<Crypto, BigDecimal> function) {
        double oneDayStats = getStats(endDate.minus(1, ChronoUnit.DAYS), endDate, function);
        double oneWeekStats = getStats(endDate.minus(1, ChronoUnit.DAYS), endDate, function);
        double oneMonthStats = getStats(endDate.minus(1, ChronoUnit.DAYS), endDate, function);
        return statsFactory.create(oneDayStats, oneWeekStats, oneMonthStats);
    }

    private double getStats(Instant startDate, Instant endDate, Function<Crypto, BigDecimal> function) {
        List<Crypto> lastDayCryptos = cryptoRepository.findByCreatedAtBetween(startDate.toEpochMilli(),
                                                                              endDate.toEpochMilli());
        int size = lastDayCryptos.size();
        long count = lastDayCryptos.stream()
                .filter(crypto -> function.apply(crypto).compareTo(crypto.getNextDayMaxPrice()) < 0)
                .count();
        return size > 0 ? (double) count / (double) size * 100 : -1;
    }

    public AverageProfit getAverageProfit() {
        BigDecimal average2H = getAverage(CryptoType.TYPE_2H, Crypto::getPriceToSellPercentage2h);
        BigDecimal average5H = getAverage(CryptoType.TYPE_5H, Crypto::getPriceToSellPercentage5h);
        BigDecimal average10H = getAverage(CryptoType.TYPE_10H, Crypto::getPriceToSellPercentage10h);
        BigDecimal average24H = getAverage(CryptoType.TYPE_24H, Crypto::getPriceToSellPercentage24h);
        return new AverageProfit(average2H, average5H, average10H, average24H);
    }

    private BigDecimal getAverage(CryptoType byCryptoType, Function<Crypto, BigDecimal> function) {
        List<Crypto> cryptos = cryptoRepository.findByCryptoType(byCryptoType);
        int size = cryptos.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        return cryptos.stream()
                .map(function)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(size), 8, BigDecimal.ROUND_UP);
    }


    @Time
    @Scheduled(cron = "0 */10 * * * ?")
    public void updateAll() {
        BinanceApi api = new BinanceApi();
        Instant now = Instant.now();
        Instant beforeOneDay = now.minus(1, ChronoUnit.DAYS);
        cryptoRepository.findUniqueSymbols(beforeOneDay.toEpochMilli())
                .forEach(symbol -> saveData(api, symbol, beforeOneDay));
    }

    private void saveData(BinanceApi api, String symbol, Instant beforeOneDay) {
        System.out.println("Updating symbol: " + symbol);
        List<BinanceCandlestick> klines;
        try {
            klines = api.klines(new BinanceSymbol(symbol), ONE_DAY, 1, null);
        } catch (BinanceApiException e) {
            throw new RuntimeException("Problem during binance klines", e);
        }
        BinanceCandlestick binanceCandlestick = klines.get(0);
        cryptoRepository.update(binanceCandlestick.getHigh(), symbol, beforeOneDay.toEpochMilli());
    }

    @Async
    @Time
    void saveAll(List<CryptoDto> cryptoDtos) {
        List<Crypto> cryptos = cryptoDtos.stream()
                .map(cryptoDto -> cryptoDtoFactory.createCrypto(cryptoDto))
                .collect(Collectors.toList());
        Long now = Instant.now().toEpochMilli();
        save(cryptos, Crypto::getPriceToSellPercentage2h, CryptoType.TYPE_2H, now);
        save(cryptos, Crypto::getPriceToSellPercentage5h, CryptoType.TYPE_5H, now);
        save(cryptos, Crypto::getPriceToSellPercentage10h, CryptoType.TYPE_10H, now);
        save(cryptos, Crypto::getPriceToSellPercentage24h, CryptoType.TYPE_24H, now);
    }

    private void save(List<Crypto> cryptos,
                      Function<Crypto, BigDecimal> function,
                      CryptoType cryptoType,
                      Long now) {
        List<Crypto> filteredCryptos = cryptos.stream()
                .filter(crypto -> function.apply(crypto).compareTo(new BigDecimal("0.5")) > 0)
                .peek(crypto -> crypto.setCryptoType(cryptoType))
                .peek(crypto -> crypto.setCreatedAt(now))
                .peek(crypto -> crypto.setId(null))
                .collect(Collectors.toList());
        cryptoRepository.saveAll(filteredCryptos);
    }
}

