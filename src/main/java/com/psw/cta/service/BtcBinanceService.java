package com.psw.cta.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import com.psw.cta.service.dto.*;
import com.psw.cta.service.factory.CryptoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.psw.cta.service.dto.BinanceInterval.FIFTEEN_MIN;
import static com.psw.cta.service.dto.BinanceInterval.ONE_DAY;

@Service
class BtcBinanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtcBinanceService.class);

    private CryptoService cryptoService;
    private BinanceService binanceService;
    private CacheService cacheService;
    private CryptoFactory cryptoFactory;

    BtcBinanceService(CryptoService cryptoService,
                      BinanceService binanceService,
                      CacheService cacheService,
                      CryptoFactory cryptoFactory) {
        this.cryptoService = cryptoService;
        this.binanceService = binanceService;
        this.cacheService = cacheService;
        this.cryptoFactory = cryptoFactory;
    }

    @Time
    @Scheduled(cron = "0 * * * * ?")
    public void downloadData() {
        try {
            Instant now = Instant.now();
            Long nowMillis = now.toEpochMilli();
            LocalDateTime nowDate = LocalDateTime.ofInstant(now, ZoneId.of("Europe/Vienna"));
            List<LinkedTreeMap<String, Object>> tickers = getAll24hTickers();
            List<Crypto> cryptos = binanceService.exchangeInfo()
                    .getSymbols()
                    .parallelStream()
                    .map(CryptoDto::new)
                    .filter(dto -> dto.getBinanceExchangeSymbol().getSymbol().getSymbol().endsWith("BTC"))
                    .filter(dto -> dto.getBinanceExchangeSymbol().getStatus().equals("TRADING"))
                    .peek(dto -> dto.setThreeMonthsCandleStickData(getCandleStickData(dto, ONE_DAY, 90)))
                    .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
                    .peek(dto -> dto.setTicker24hr(get24hTicker(tickers, dto)))
                    .peek(dto -> dto.setVolume(calculateVolume(dto)))
                    .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                    .peek(dto -> dto.setDepth20(getDepth(dto)))
                    .peek(dto -> dto.setCurrentPrice(calculateCurrentPrice(dto)))
                    .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                    .peek(dto -> dto.setFifteenMinutesCandleStickData(getCandleStickData(dto, FIFTEEN_MIN, 96)))
                    .peek(dto -> dto.setSumDiffsPerc(calculateSumDiffsPerc(dto, 4)))
                    .peek(dto -> dto.setSumDiffsPerc10h(calculateSumDiffsPerc(dto, 40)))
                    .peek(dto -> dto.setPriceToSell(calculatePriceToSell(dto)))
                    .peek(dto -> dto.setPriceToSellPercentage(calculatePriceToSellPercentage(dto)))
                    .peek(dto -> dto.setWeight(calculateWeight(dto)))
                    .filter(dto -> dto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                    .filter(dto -> dto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                    .filter(dto -> dto.getSumDiffsPerc10h().compareTo(new BigDecimal("400")) < 0)
                    .map(cryptoDto -> cryptoFactory.createCrypto(cryptoDto, nowMillis, nowDate))
                    .collect(Collectors.toList());
            int cryptosSize = cryptos.size();
            LOGGER.info("Actual number of cryptos: " + cryptosSize);
            cacheService.setActualCryptos(new ActualCryptos(cryptos.stream()
                                                                    .map(crypto -> (CryptoResult) crypto)
                                                                    .collect(Collectors.toList())));
            cryptoService.saveAll(cryptos);
        } catch (CryptoTradeAnalyserException e) {
            e.printStackTrace();
        }
    }

    private List<LinkedTreeMap<String, Object>> getAll24hTickers() throws CryptoTradeAnalyserException {
        final JsonArray json = binanceService.ticker24hr();
        return getObject(json);
    }

    private LinkedTreeMap<String, Object> get24hTicker(List<LinkedTreeMap<String, Object>> tickers,
                                                       CryptoDto cryptoDto) {
        final String symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol();
        return tickers.parallelStream()
                .filter(map -> map.containsKey("symbol") && map.get("symbol").equals(symbol))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    private BigDecimal calculateVolume(CryptoDto cryptoDto) {
        if (cryptoDto.getTicker24hr().containsKey("quoteVolume")) {
            return new BigDecimal((String) cryptoDto.getTicker24hr().get("quoteVolume"));
        } else {
            return BigDecimal.ZERO;
        }
    }

    private LinkedTreeMap<String, Object> getDepth(CryptoDto cryptoDto) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            final JsonObject depth = binanceService.depth(symbol);
            return getObject(depth);
        } catch (CryptoTradeAnalyserException e) {
            throw new RuntimeException(e);
        }
    }

    private <R, T extends JsonElement> R getObject(T json) {
        final TypeToken<R> typeToken = new TypeToken<R>() {
        };
        return new Gson().fromJson(json, typeToken.getType());
    }

    @SuppressWarnings({"unchecked"})
    private BigDecimal calculateCurrentPrice(CryptoDto cryptoDto) {
        ArrayList<Object> asks = (ArrayList<Object>) cryptoDto.getDepth20().get("asks");
        return asks.parallelStream()
                .map(data -> (new BigDecimal((String) ((ArrayList<Object>) data).get(0))))
                .min(Comparator.naturalOrder())
                .orElseThrow(RuntimeException::new);
    }

    private List<BinanceCandlestick> getCandleStickData(CryptoDto cryptoDto, BinanceInterval interval, int limit) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            return binanceService.klines(symbol, interval, limit);
        } catch (CryptoTradeAnalyserException e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto, int numberOfDataToKeep) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - numberOfDataToKeep < 0) return BigDecimal.ZERO;
        return calculateSumDiffsPercentage(cryptoDto, size - numberOfDataToKeep);
    }

    private BigDecimal calculateSumDiffsPercentage(CryptoDto cryptoDto, int size) {
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .skip(size)
                .map(data -> getPercentualDifference(data, cryptoDto.getCurrentPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPercentualDifference(BinanceCandlestick data, BigDecimal currentPrice) {
        BigDecimal absoluteValue = getAverageValue(data);
        BigDecimal relativeValue = absoluteValue.multiply(new BigDecimal("100"))
                .divide(currentPrice, 8, BigDecimal.ROUND_UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    private BigDecimal getAverageValue(BinanceCandlestick data) {
        return data.getOpen()
                .add(data.getClose())
                .add(data.getHigh())
                .add(data.getLow())
                .divide(new BigDecimal("4"), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - 4 < 0) return BigDecimal.ZERO;
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .skip(size - 4)
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO)
                .subtract(cryptoDto.getCurrentPrice())
                .divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP)
                .add(cryptoDto.getCurrentPrice());
    }

    private BigDecimal calculatePriceToSellPercentage(CryptoDto cryptoDto) {
        BigDecimal priceToSell = cryptoDto.getPriceToSell();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        return priceToSell.multiply(new BigDecimal("100"))
                .divide(currentPrice, 8, BigDecimal.ROUND_UP)
                .subtract(new BigDecimal("100"));
    }

    @SuppressWarnings({"unchecked"})
    private BigDecimal calculateWeight(CryptoDto cryptoDto) {
        BigDecimal priceToSell = cryptoDto.getPriceToSell();
        BigDecimal priceToSellPercentage = cryptoDto.getPriceToSellPercentage();
        BigDecimal ratio;
        ArrayList<Object> asks = (ArrayList<Object>) cryptoDto.getDepth20().get("asks");
        final BigDecimal sum = asks.parallelStream()
                .filter(data -> (new BigDecimal((String) ((ArrayList<Object>) data).get(0))).compareTo(priceToSell) < 0)
                .map(data -> (new BigDecimal(((String) ((ArrayList<Object>) data).get(0)))
                        .multiply(new BigDecimal((String) ((ArrayList<Object>) data).get(1)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0 && priceToSell.compareTo(cryptoDto.getCurrentPrice()) > 0) {
            ratio = new BigDecimal(Double.MAX_VALUE);
        } else if (sum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = BigDecimal.ZERO;
        } else {
            ratio = cryptoDto.getVolume().divide(sum, 8, BigDecimal.ROUND_UP);
        }
        return priceToSellPercentage.multiply(ratio);
    }
}
