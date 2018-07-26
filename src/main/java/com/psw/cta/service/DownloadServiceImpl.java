package com.psw.cta.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.psw.cta.service.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.BinanceCandlestick;
import com.webcerebrium.binance.datatype.BinanceExchangeInfo;
import com.webcerebrium.binance.datatype.BinanceExchangeSymbol;
import com.webcerebrium.binance.datatype.BinanceInterval;
import com.webcerebrium.binance.datatype.BinanceSymbol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.webcerebrium.binance.datatype.BinanceInterval.FIFTEEN_MIN;
import static com.webcerebrium.binance.datatype.BinanceInterval.ONE_DAY;

@Service
@Slf4j
class DownloadServiceImpl {

    @Autowired
    private LoggingServiceImpl loggingService;

    @Autowired
    private CryptoService cryptoService;

    @Time
    @Scheduled(cron = "0 * * * * ?")
    public void downloadData() {
        BinanceApi api = new BinanceApi();
        try {
            List<CryptoDto> cryptoDtos = createCryptoDtos(api);
            log.info("Number of dtos at begining: " + cryptoDtos.size());
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getBinanceExchangeSymbol().getSymbol().getSymbol().endsWith("BTC"))
                    .filter(dto -> dto.getBinanceExchangeSymbol().getStatus().equals("TRADING"))
                    .collect(Collectors.toList());
            log.info("Number of dtos after 1. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setDepth20(getDepth(api, cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setCurrentPrice(provideCurrentPrice(cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setFifteenMinutesCandleStickData(getCandleStickData(api, cryptoDto, FIFTEEN_MIN, 96)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setFifteenMinutesMaxToCurrentDifferent(
                    calculateFifteenMinutesMaxToCurrent(cryptoDto)));
            cryptoDtos.parallelStream().forEach(
                    cryptoDto -> cryptoDto.setFifteenMinutesPercentageLoss(calculate15MinPercentageLoss(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getFifteenMinutesPercentageLoss().compareTo(new BigDecimal("0.5")) > 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 2. filtration: " + cryptoDtos.size());
            List<LinkedTreeMap<String, Object>> tickers = getAll24hTickers(api);
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setTicker24hr(get24hTicker(tickers, cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getTicker24hr().containsKey("quoteVolume") &&
                            (new BigDecimal((String) dto.getTicker24hr().get("quoteVolume"))
                                    .compareTo(new BigDecimal("100")) > 0))
                    .collect(Collectors.toList());
            log.info("Number of dtos after 3. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setThreeDaysCandleStickData(
                    getCandleStickData(api, cryptoDto, ONE_DAY, 500)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setLastThreeDaysAveragePrice(calculateLastThreeDaysAveragePrice(cryptoDto)));
            cryptoDtos.parallelStream().forEach(
                    cryptoDto -> cryptoDto.setLastThreeDaysMaxPrice(calculateLastThreeDaysMaxPrice(cryptoDto)));
            cryptoDtos.parallelStream().forEach(
                    cryptoDto -> cryptoDto.setLastThreeDaysMinPrice(calculateLastThreeDaysMinPrice(cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setLastThreeDaysMaxMinDiffPercent(calculateLastThreeDaysMaxMinDiffPercent(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getLastThreeDaysAveragePrice().compareTo(dto.getCurrentPrice()) > 0)
                    .filter(dto -> dto.getLastThreeDaysMaxMinDiffPercent().compareTo(new BigDecimal("30")) < 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 4. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setVolume(calculateVolume(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getVolume().compareTo(new BigDecimal("1000")) > 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 5. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setPriceToSell(calculatePriceToSell(cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setRatio(calculateRatio(cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setWeight(calculateWeight(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getWeight().compareTo(new BigDecimal("100000")) > 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 6. filtration: " + cryptoDtos.size());
            loggingService.log(cryptoDtos);
            cryptoService.saveAll(cryptoDtos);
        } catch (BinanceApiException e) {
            e.printStackTrace();
        }
    }

    private List<CryptoDto> createCryptoDtos(BinanceApi api) throws BinanceApiException {
        List<CryptoDto> cryptoDtos = new ArrayList<>();
        log.info("Getting exchange info");
        BinanceExchangeInfo binanceExchangeInfo = api.exchangeInfo();
        for (BinanceExchangeSymbol symbol : binanceExchangeInfo.getSymbols()) {
            CryptoDto cryptoDto = new CryptoDto();
            cryptoDto.setBinanceExchangeSymbol(symbol);
            cryptoDtos.add(cryptoDto);
        }
        return cryptoDtos;
    }

    @SuppressWarnings({"unchecked"})
    private BigDecimal provideCurrentPrice(CryptoDto cryptoDto) {
        ArrayList<Object> asks = (ArrayList<Object>) cryptoDto.getDepth20().get("asks");
        return asks.stream()
                .map(data -> (new BigDecimal((String) ((ArrayList<Object>) data).get(0))))
                .min(Comparator.naturalOrder())
                .orElseThrow(RuntimeException::new);
    }

    private List<LinkedTreeMap<String, Object>> getAll24hTickers(BinanceApi api) throws BinanceApiException {
        final JsonArray json = api.ticker24hr();
        return getObject(json);
    }

    private LinkedTreeMap<String, Object> get24hTicker(List<LinkedTreeMap<String, Object>> tickers,
                                                       CryptoDto cryptoDto) {
        final String symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol().getSymbol();
        return tickers.stream()
                .filter(map -> map.containsKey("symbol") && map.get("symbol").equals(symbol))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    private static List<BinanceCandlestick> getCandleStickData(BinanceApi api,
                                                               CryptoDto cryptoDto,
                                                               BinanceInterval interval,
                                                               int limit) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            return api.klines(symbol, interval, limit, null);
        } catch (BinanceApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static BigDecimal calculateLastThreeDaysAveragePrice(CryptoDto cryptoDto) {
        return cryptoDto.getThreeDaysCandleStickData().stream()
                .sorted(Comparator.comparing(BinanceCandlestick::getOpenTime).reversed())
                .limit(3)
                .map(data -> data.getOpen().add((data.getLow())).add(data.getHigh()).add(data.getClose()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal("12"), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculateLastThreeDaysMaxPrice(CryptoDto cryptoDto) {
        return cryptoDto.getThreeDaysCandleStickData().stream()
                .sorted(Comparator.comparing(BinanceCandlestick::getOpenTime).reversed())
                .limit(3)
                .map(BinanceCandlestick::getHigh)
                .max(BigDecimal::compareTo)
                .orElseThrow(RuntimeException::new);
    }

    private BigDecimal calculateLastThreeDaysMinPrice(CryptoDto cryptoDto) {
        return cryptoDto.getThreeDaysCandleStickData().stream()
                .sorted(Comparator.comparing(BinanceCandlestick::getOpenTime).reversed())
                .limit(3)
                .map(BinanceCandlestick::getLow)
                .min(BigDecimal::compareTo)
                .orElseThrow(RuntimeException::new);
    }

    private BigDecimal calculateLastThreeDaysMaxMinDiffPercent(CryptoDto cryptoDto) {
        final BigDecimal lastThreeDaysMaxPrice = cryptoDto.getLastThreeDaysMaxPrice();
        final BigDecimal lastThreeDaysMinPrice = cryptoDto.getLastThreeDaysMinPrice();
        final BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        final BigDecimal differentMaxMin = lastThreeDaysMaxPrice.subtract(lastThreeDaysMinPrice);
        return differentMaxMin.multiply(new BigDecimal("100")).divide(currentPrice, 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculateVolume(CryptoDto cryptoDto) {
        return cryptoDto.getThreeDaysCandleStickData().stream()
                .map(BinanceCandlestick::getQuoteAssetVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateFifteenMinutesMaxToCurrent(CryptoDto cryptoDto) {
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .sorted(Comparator.comparing(BinanceCandlestick::getOpenTime).reversed())
                .limit(5)
                .map(BinanceCandlestick::getHigh)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal("5"), 8, BigDecimal.ROUND_UP)
                .subtract(cryptoDto.getCurrentPrice());
    }

    private static BigDecimal calculate15MinPercentageLoss(CryptoDto cryptoDto) {
        final BigDecimal high = cryptoDto.getFifteenMinutesMaxToCurrentDifferent();
        final BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        return high.multiply(new BigDecimal("100")).divide(currentPrice, 8, BigDecimal.ROUND_UP);
    }

    private LinkedTreeMap<String, Object> getDepth(BinanceApi api, CryptoDto cryptoDto) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            final JsonObject depth = api.depth(symbol, 20);
            return getObject(depth);
        } catch (BinanceApiException e) {
            throw new RuntimeException(e);
        }
    }

    private <R, T extends JsonElement> R getObject(T json) {
        final TypeToken<R> typeToken = new TypeToken<R>() {
        };
        return new Gson().fromJson(json, typeToken.getType());
    }

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto) {
        final BigDecimal fifteenMinutesPercentageLoss = cryptoDto.getFifteenMinutesPercentageLoss();
        final BigDecimal realLoss = fifteenMinutesPercentageLoss.multiply(cryptoDto.getCurrentPrice())
                .divide(new BigDecimal("100"), 8, BigDecimal.ROUND_UP);
        final BigDecimal realLossOf75Percent = realLoss.multiply(new BigDecimal("0.75"));
        return cryptoDto.getCurrentPrice().add(realLossOf75Percent);
    }

    @SuppressWarnings({"unchecked"})
    private BigDecimal calculateRatio(CryptoDto cryptoDto) {
        final BigDecimal priceToSell = cryptoDto.getPriceToSell();
        ArrayList<Object> asks = (ArrayList<Object>) cryptoDto.getDepth20().get("asks");
        final BigDecimal sum = asks.stream()
                .filter(data -> (new BigDecimal((String) ((ArrayList<Object>) data).get(0))).compareTo(priceToSell) < 0)
                .map(data -> (new BigDecimal(((String) ((ArrayList<Object>) data).get(0)))
                        .multiply(new BigDecimal((String) ((ArrayList<Object>) data).get(1)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0 && priceToSell.compareTo(cryptoDto.getCurrentPrice()) > 0) {
            return new BigDecimal(Double.MAX_VALUE);
        } else if (sum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        } else {
            return cryptoDto.getVolume().divide(sum, 8, BigDecimal.ROUND_UP);
        }
    }

    private BigDecimal calculateWeight(CryptoDto cryptoDto) {
        return cryptoDto.getFifteenMinutesPercentageLoss().divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP)
                .multiply(cryptoDto.getRatio());
    }
}
