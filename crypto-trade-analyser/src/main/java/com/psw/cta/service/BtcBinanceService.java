package com.psw.cta.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.psw.cta.aspect.Time;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import com.psw.cta.service.dto.BinanceCandlestick;
import com.psw.cta.service.dto.BinanceInterval;
import com.psw.cta.service.dto.BinanceSymbol;
import com.psw.cta.service.dto.CryptoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
class BtcBinanceService {

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private BinanceService binanceService;

    @Time
    @Scheduled(cron = "0 * * * * ?")
    public void downloadData() {
        try {
            List<LinkedTreeMap<String, Object>> tickers = getAll24hTickers();
            List<CryptoDto> cryptoDtos = binanceService.exchangeInfo()
                    .getSymbols()
                    .parallelStream()
                    .map(CryptoDto::new)
                    .filter(dto -> dto.getBinanceExchangeSymbol().getSymbol().getSymbol().endsWith("BTC"))
                    .filter(dto -> dto.getBinanceExchangeSymbol().getStatus().equals("TRADING"))
                    .peek(cryptoDto -> cryptoDto.setTicker24hr(get24hTicker(tickers, cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setVolume(calculateVolume(cryptoDto)))
                    .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                    .peek(cryptoDto -> cryptoDto.setDepth20(getDepth(cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setCurrentPrice(calculateCurrentPrice(cryptoDto)))
                    .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                    .peek(cryptoDto -> cryptoDto.setFifteenMinutesCandleStickData(getCandleStickData(cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc1Day(calculateSumDiffsPerc(cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc1h(calculateSumDiffsPerc(cryptoDto, 4)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc2h(calculateSumDiffsPerc(cryptoDto, 8)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc5h(calculateSumDiffsPerc(cryptoDto, 20)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell1h(calculatePriceToSell(cryptoDto, 4)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell2h(calculatePriceToSell(cryptoDto, 8)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell5h(calculatePriceToSell(cryptoDto, 20)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage1h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell1h(),
                                                                                                           cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage2h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell2h(),
                                                                                                           cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage5h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell5h(),
                                                                                                           cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setWeight1h(calculateWeight(cryptoDto,
                                                                             cryptoDto.getPriceToSell1h(),
                                                                             cryptoDto.getPriceToSellPercentage1h())))
                    .peek(cryptoDto -> cryptoDto.setWeight2h(calculateWeight(cryptoDto,
                                                                             cryptoDto.getPriceToSell2h(),
                                                                             cryptoDto.getPriceToSellPercentage2h())))
                    .peek(cryptoDto -> cryptoDto.setWeight5h(calculateWeight(cryptoDto,
                                                                             cryptoDto.getPriceToSell5h(),
                                                                             cryptoDto.getPriceToSellPercentage5h())))
                    .collect(Collectors.toList());
            log.info("Actual number of cryptos: " + cryptoDtos.size());
            cryptoService.saveAll(cryptoDtos);
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
            final JsonObject depth = binanceService.depth(symbol, 20);
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

    private List<BinanceCandlestick> getCandleStickData(CryptoDto cryptoDto) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            return binanceService.klines(symbol, BinanceInterval.FIFTEEN_MIN, 96);
        } catch (CryptoTradeAnalyserException e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto, int numberOfDataToKeep) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - numberOfDataToKeep < 0) return BigDecimal.ZERO;
        return calculateSumDiffsPercentage(cryptoDto, size - numberOfDataToKeep);
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        return calculateSumDiffsPercentage(cryptoDto, size);
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

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto, int numberOfDataToKeep) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - numberOfDataToKeep < 0) return BigDecimal.ZERO;
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .skip(size - numberOfDataToKeep)
                .map(BinanceCandlestick::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO)
                .subtract(cryptoDto.getCurrentPrice())
                .divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP)
                .add(cryptoDto.getCurrentPrice());
    }

    private BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal currentPrice) {
        return priceToSell.multiply(new BigDecimal("100"))
                .divide(currentPrice, 8, BigDecimal.ROUND_UP)
                .subtract(new BigDecimal("100"));
    }

    @SuppressWarnings({"unchecked"})
    private BigDecimal calculateWeight(CryptoDto cryptoDto, BigDecimal priceToSell, BigDecimal priceToSellPercentage) {
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
