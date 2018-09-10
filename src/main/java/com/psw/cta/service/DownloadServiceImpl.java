package com.psw.cta.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.psw.cta.entity.Crypto;
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
            List<LinkedTreeMap<String, Object>> tickers = getAll24hTickers(api);
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setTicker24hr(get24hTicker(tickers, cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setVolume(provideVolume(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 2. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setDepth20(getDepth(api, cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setCurrentPrice(provideCurrentPrice(cryptoDto)));
            cryptoDtos = cryptoDtos.stream()
                    .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                    .collect(Collectors.toList());
            log.info("Number of dtos after 3. filtration: " + cryptoDtos.size());
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setFifteenMinutesCandleStickData(getCandleStickData(api, cryptoDto)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setSumDiffsPerc2h(calculateSumDiffsPerc(cryptoDto, 8)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setSumDiffsPerc5h(calculateSumDiffsPerc(cryptoDto, 20)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setSumDiffsPerc10h(calculateSumDiffsPerc(cryptoDto, 40)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto
                    .setSumDiffsPerc24h(calculateSumDiffsPerc(cryptoDto, 96)));
            cryptoDtos.parallelStream()
                    .forEach(cryptoDto -> cryptoDto.setPriceToSell2h(calculatePriceToSell(cryptoDto, 8)));
            cryptoDtos.parallelStream()
                    .forEach(cryptoDto -> cryptoDto.setPriceToSell5h(calculatePriceToSell(cryptoDto, 20)));
            cryptoDtos.parallelStream()
                    .forEach(cryptoDto -> cryptoDto.setPriceToSell10h(calculatePriceToSell(cryptoDto, 40)));
            cryptoDtos.parallelStream()
                    .forEach(cryptoDto -> cryptoDto.setPriceToSell24h(calculatePriceToSell(cryptoDto, 96)));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setPriceToSellPercentage2h(
                    calculatePriceToSellPercentage(cryptoDto.getPriceToSell2h(),
                                                   cryptoDto.getCurrentPrice())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setPriceToSellPercentage5h(
                    calculatePriceToSellPercentage(cryptoDto.getPriceToSell5h(),
                                                   cryptoDto.getCurrentPrice())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setPriceToSellPercentage10h(
                    calculatePriceToSellPercentage(cryptoDto.getPriceToSell10h(),
                                                   cryptoDto.getCurrentPrice())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setPriceToSellPercentage24h(
                    calculatePriceToSellPercentage(cryptoDto.getPriceToSell24h(),
                                                   cryptoDto.getCurrentPrice())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setWeight2h(calculateWeight(cryptoDto,
                                                                                                   cryptoDto.getPriceToSell2h(),
                                                                                                   cryptoDto.getPriceToSellPercentage2h())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setWeight5h(calculateWeight(cryptoDto,
                                                                                                   cryptoDto.getPriceToSell5h(),
                                                                                                   cryptoDto.getPriceToSellPercentage5h())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setWeight10h(calculateWeight(cryptoDto,
                                                                                                    cryptoDto.getPriceToSell10h(),
                                                                                                    cryptoDto.getPriceToSellPercentage10h())));
            cryptoDtos.parallelStream().forEach(cryptoDto -> cryptoDto.setWeight24h(calculateWeight(cryptoDto,
                                                                                                    cryptoDto.getPriceToSell24h(),
                                                                                                    cryptoDto.getPriceToSellPercentage24h())));
            loggingService.log(cryptoDtos);
            cryptoService.saveAll(cryptoDtos);
            cryptoService.updateAll(api);
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

    private BigDecimal provideVolume(CryptoDto cryptoDto) {
        if (cryptoDto.getTicker24hr().containsKey("quoteVolume")) {
            return new BigDecimal((String) cryptoDto.getTicker24hr().get("quoteVolume"));
        } else {
            return BigDecimal.ZERO;
        }
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

    @SuppressWarnings({"unchecked"})
    private BigDecimal provideCurrentPrice(CryptoDto cryptoDto) {
        ArrayList<Object> asks = (ArrayList<Object>) cryptoDto.getDepth20().get("asks");
        return asks.stream()
                .map(data -> (new BigDecimal((String) ((ArrayList<Object>) data).get(0))))
                .min(Comparator.naturalOrder())
                .orElseThrow(RuntimeException::new);
    }

    private static List<BinanceCandlestick> getCandleStickData(BinanceApi api,
                                                               CryptoDto cryptoDto) {
        final BinanceSymbol symbol = cryptoDto.getBinanceExchangeSymbol().getSymbol();
        try {
            return api.klines(symbol, BinanceInterval.FIFTEEN_MIN, 96, null);
        } catch (BinanceApiException e) {
            throw new RuntimeException(e);
        }
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto, int dataToSkip) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .skip(size - dataToSkip)
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

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto, int dataToSkip) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
                .skip(size - dataToSkip)
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
        final BigDecimal sum = asks.stream()
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
