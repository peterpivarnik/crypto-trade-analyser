package com.psw.cta.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.psw.cta.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.webcerebrium.binance.api.BinanceApi;
import com.webcerebrium.binance.api.BinanceApiException;
import com.webcerebrium.binance.datatype.*;
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
    private CryptoCollectorService cryptoService;

    @Time
    @Scheduled(cron = "0 * * * * ?")
    public void downloadData() {
        BinanceApi api = new BinanceApi();
        try {
            List<LinkedTreeMap<String, Object>> tickers = getAll24hTickers(api);
            List<CryptoDto> cryptoDtos = createCryptoDtos(api).parallelStream()
                    .filter(dto -> dto.getBinanceExchangeSymbol().getSymbol().getSymbol().endsWith("BTC"))
                    .filter(dto -> dto.getBinanceExchangeSymbol().getStatus().equals("TRADING"))
                    .peek(cryptoDto -> cryptoDto.setTicker24hr(get24hTicker(tickers, cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setVolume(provideVolume(cryptoDto)))
                    .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                    .peek(cryptoDto -> cryptoDto.setDepth20(getDepth(api, cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setCurrentPrice(provideCurrentPrice(cryptoDto)))
                    .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                    .peek(cryptoDto -> cryptoDto.setFifteenMinutesCandleStickData(getCandleStickData(api, cryptoDto)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc2h(calculateSumDiffsPerc(cryptoDto, 8)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc5h(calculateSumDiffsPerc(cryptoDto, 20)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc10h(calculateSumDiffsPerc(cryptoDto, 40)))
                    .peek(cryptoDto -> cryptoDto.setSumDiffsPerc24h(calculateSumDiffsPerc(cryptoDto, 96)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell2h(calculatePriceToSell(cryptoDto, 8)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell5h(calculatePriceToSell(cryptoDto, 20)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell10h(calculatePriceToSell(cryptoDto, 40)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSell24h(calculatePriceToSell(cryptoDto, 96)))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage2h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell2h(),
                                                                                                           cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage5h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell5h(),
                                                                                                           cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage10h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell10h(),
                                                                                                            cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setPriceToSellPercentage24h(calculatePriceToSellPercentage(cryptoDto.getPriceToSell24h(),
                                                                                                            cryptoDto.getCurrentPrice())))
                    .peek(cryptoDto -> cryptoDto.setWeight2h(calculateWeight(cryptoDto,
                                                                             cryptoDto.getPriceToSell2h(),
                                                                             cryptoDto.getPriceToSellPercentage2h())))
                    .peek(cryptoDto -> cryptoDto.setWeight5h(calculateWeight(cryptoDto,
                                                                             cryptoDto.getPriceToSell5h(),
                                                                             cryptoDto.getPriceToSellPercentage5h())))
                    .peek(cryptoDto -> cryptoDto.setWeight10h(calculateWeight(cryptoDto,
                                                                              cryptoDto.getPriceToSell10h(),
                                                                              cryptoDto.getPriceToSellPercentage10h())))
                    .peek(cryptoDto -> cryptoDto.setWeight24h(calculateWeight(cryptoDto,
                                                                              cryptoDto.getPriceToSell24h(),
                                                                              cryptoDto.getPriceToSellPercentage24h())))
                    .collect(Collectors.toList());
            log.info("Actual number of cryptos: " + cryptoDtos.size());
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

    private List<LinkedTreeMap<String, Object>> getAll24hTickers(BinanceApi api) throws BinanceApiException {
        final JsonArray json = api.ticker24hr();
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
        return asks.parallelStream()
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
        if (size - dataToSkip < 0) return BigDecimal.ZERO;
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
        if (size - dataToSkip < 0) return BigDecimal.ZERO;
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
