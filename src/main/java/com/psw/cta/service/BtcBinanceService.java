package com.psw.cta.service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.aspect.Time;
import com.psw.cta.entity.Crypto;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.factory.CryptoFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class BtcBinanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtcBinanceService.class);

    private CryptoFactory cryptoFactory;
    private BinanceApiRestClient binanceApiRestClient;

    BtcBinanceService(CryptoFactory cryptoFactory) {
        this.cryptoFactory = cryptoFactory;
        this.binanceApiRestClient = new BinanceApiRestClientImpl("", "");
    }

    @Time
    @Scheduled(cron = "0 * * * * ?")
    public void downloadData() {
        try {
            Instant now = Instant.now();
            Long nowMillis = now.toEpochMilli();
            LocalDateTime nowDate = LocalDateTime.ofInstant(now, ZoneId.of("Europe/Vienna"));
            List<TickerStatistics> tickers = getAll24hTickers();
            List<Crypto> cryptos = binanceApiRestClient.getExchangeInfo()
                .getSymbols()
                .parallelStream()
                .map(CryptoDto::new)
                .filter(dto -> dto.getSymbolInfo().getSymbol().endsWith("BTC"))
                .filter(dto -> dto.getSymbolInfo().getStatus() == SymbolStatus.TRADING)
                .peek(dto -> dto.setThreeMonthsCandleStickData(getCandleStickData(dto, CandlestickInterval.DAILY, 90)))
                .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
                .peek(dto -> dto.setTicker24hr(get24hTicker(tickers, dto)))
                .peek(dto -> dto.setVolume(calculateVolume(dto)))
                .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                .peek(dto -> dto.setDepth20(getDepth(dto)))
                .peek(dto -> dto.setCurrentPrice(calculateCurrentPrice(dto)))
                .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                .peek(dto -> dto.setFifteenMinutesCandleStickData(getCandleStickData(dto, CandlestickInterval.FIFTEEN_MINUTES, 96)))
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
        } catch (CryptoTradeAnalyserException e) {
            e.printStackTrace();
        }
    }

    private List<TickerStatistics> getAll24hTickers() {
        return binanceApiRestClient.getAll24HrPriceStatistics();
    }

    private TickerStatistics get24hTicker(List<TickerStatistics> tickers, CryptoDto cryptoDto) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return tickers.parallelStream()
            .filter(ticker -> ticker.getSymbol().equals(symbol))
            .findAny()
            .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    private BigDecimal calculateVolume(CryptoDto cryptoDto) {
        return new BigDecimal(cryptoDto.getTicker24hr().getVolume());
    }

    private OrderBook getDepth(CryptoDto cryptoDto) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return binanceApiRestClient.getOrderBook(symbol, 20);
    }

    private BigDecimal calculateCurrentPrice(CryptoDto cryptoDto) {
        return cryptoDto.getDepth20()
            .getAsks()
            .parallelStream()
            .map(OrderBookEntry::getPrice)
            .map(BigDecimal::new)
            .min(Comparator.naturalOrder())
            .orElseThrow(RuntimeException::new);
    }

    private List<Candlestick> getCandleStickData(CryptoDto cryptoDto, CandlestickInterval interval, Integer limit) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return binanceApiRestClient.getCandlestickBars(symbol, interval, limit, null, null);
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto, int numberOfDataToKeep) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - numberOfDataToKeep < 0) {
            return BigDecimal.ZERO;
        }
        return calculateSumDiffsPercentage(cryptoDto, size - numberOfDataToKeep);
    }

    private BigDecimal calculateSumDiffsPercentage(CryptoDto cryptoDto, int size) {
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
            .skip(size)
            .map(data -> getPercentualDifference(data, cryptoDto.getCurrentPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPercentualDifference(Candlestick data, BigDecimal currentPrice) {
        BigDecimal absoluteValue = getAverageValue(data);
        BigDecimal relativeValue = absoluteValue.multiply(new BigDecimal("100"))
            .divide(currentPrice, 8, BigDecimal.ROUND_UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    private BigDecimal getAverageValue(Candlestick data) {
        return new BigDecimal(data.getOpen())
            .add(new BigDecimal(data.getClose()))
            .add(new BigDecimal(data.getHigh()))
            .add(new BigDecimal(data.getLow()))
            .divide(new BigDecimal("4"), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - 4 < 0) {
            return BigDecimal.ZERO;
        }
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
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

    private BigDecimal calculateWeight(CryptoDto cryptoDto) {
        BigDecimal priceToSell = cryptoDto.getPriceToSell();
        BigDecimal priceToSellPercentage = cryptoDto.getPriceToSellPercentage();
        BigDecimal ratio;
        List<OrderBookEntry> asks = cryptoDto.getDepth20().getAsks();
        final BigDecimal sum = asks.parallelStream()
            .filter(data -> (new BigDecimal(data.getPrice()).compareTo(priceToSell) < 0))
            .map(data -> (new BigDecimal(data.getPrice()).multiply(new BigDecimal(data.getQty()))))
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
