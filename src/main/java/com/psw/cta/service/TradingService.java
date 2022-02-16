package com.psw.cta.service;

import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.CryptoUtils.calculateCurrentPrice;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.CryptoDto;
import com.psw.cta.dto.OrderDto;
import com.psw.cta.utils.CryptoUtils;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TradingService {

    public static final String ASSET_BNB = "BNB";
    public static final String ASSET_BTC = "BTC";
    public static final String SYMBOL_BNB_BTC = "BNBBTC";
    public static final BigDecimal MIN_PROFIT_PERCENT = new BigDecimal("0.5");

    private final InitialTradingService initialTradingService;
    private final RepeatTradingService repeatTradingService;
    private final BnbService bnbService;
    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public TradingService(InitialTradingService initialTradingService,
                          RepeatTradingService repeatTradingService,
                          BnbService bnbService,
                          BinanceApiService binanceApiService,
                          LambdaLogger logger) {
        this.initialTradingService = initialTradingService;
        this.repeatTradingService = repeatTradingService;
        this.bnbService = bnbService;
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public void startTrading() {
        logger.log("***** ***** Start of trading ***** *****");
        BigDecimal bnbBalance = bnbService.buyBnB();
        List<Order> openOrders = binanceApiService.getOpenOrders();
        BigDecimal sumFromOrders = openOrders.parallelStream()
                                             .map(order -> new BigDecimal(order.getPrice())
                                                 .multiply(new BigDecimal(order.getOrigQty())
                                                               .subtract(new BigDecimal(order.getExecutedQty()))))
                                             .reduce(BigDecimal.ZERO, BigDecimal::add);
        logger.log("Number of open orders: " + openOrders.size());
        BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
        BigDecimal bnbAmount = bnbBalance.multiply(bnbService.getCurrentBnbBtcPrice());
        BigDecimal myTotalPossibleBalance = sumFromOrders.add(myBtcBalance).add(bnbAmount);
        logger.log("My possible balance: " + myTotalPossibleBalance);
        BigDecimal myTotalBalance = binanceApiService.getMyTotalBalance();
        logger.log("My actual balance: " + myTotalBalance);
        int minOpenOrders = calculateMinNumberOfOrders(myTotalPossibleBalance, myBtcBalance);
        logger.log("Min open orders: " + minOpenOrders);
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        logger.log("totalAmounts: " + totalAmounts);

        ExchangeInfo exchangeInfo = binanceApiService.getExchangeInfo();
        List<CryptoDto> cryptoDtos = repeatTrading(openOrders, myBtcBalance, () -> getCryptoDtos(exchangeInfo), totalAmounts, exchangeInfo);
        int uniqueOpenOrdersSize = openOrders.parallelStream()
                                             .collect(toMap(Order::getSymbolWithPrice, order -> order, (order1, order2) -> order1))
                                             .values()
                                             .size();
        logger.log("Unique open orders: " + uniqueOpenOrdersSize);
        if (initialTradingService.haveBalanceForInitialTrading(binanceApiService.getMyBalance(ASSET_BTC)) && uniqueOpenOrdersSize <= minOpenOrders) {
            initTrading(() -> getCryptoDtos(cryptoDtos, exchangeInfo));
        }
    }

    private List<CryptoDto> getCryptoDtos(List<CryptoDto> cryptoDtos, ExchangeInfo exchangeInfo) {
        if (!cryptoDtos.isEmpty()) {
            return cryptoDtos;
        } else {
            return getCryptoDtos(exchangeInfo);
        }
    }

    private List<CryptoDto> getCryptoDtos(ExchangeInfo exchangeInfo) {
        logger.log("Sleep for 1 minute before get all cryptos");
        sleep(1000 * 60, logger);
        logger.log("Get all cryptos");
        List<TickerStatistics> tickers = binanceApiService.getAll24hTickers();
        List<CryptoDto> cryptoDtos = exchangeInfo.getSymbols()
                                                 .parallelStream()
                                                 .map(CryptoDto::new)
                                                 .filter(dto -> dto.getSymbolInfo().getSymbol().endsWith(ASSET_BTC))
                                                 .filter(dto -> !dto.getSymbolInfo().getSymbol().endsWith(SYMBOL_BNB_BTC))
                                                 .filter(dto -> dto.getSymbolInfo().getStatus() == SymbolStatus.TRADING)
                                                 .map(cryptoDto -> updateCryptoDtoWithVolume(cryptoDto, tickers))
                                                 .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                                 .map(dto -> dto.setThreeMonthsCandleStickData(binanceApiService.getCandleStickData(dto, DAILY, 90)))
                                                 .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
                                                 .map(this::updateCryptoDtoWithCurrentPrice)
                                                 .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                                                 .collect(Collectors.toList());
        logger.log("Cryptos count: " + cryptoDtos.size());
        return cryptoDtos;
    }

    private Map<String, BigDecimal> createTotalAmounts(List<Order> openOrders) {
        return openOrders.stream()
                         .collect(toMap(Order::getSymbol,
                                        order -> new BigDecimal(order.getPrice()).multiply(new BigDecimal(order.getOrigQty())),
                                        BigDecimal::add))
                         .entrySet()
                         .stream()
                         .sorted(Map.Entry.comparingByValue())
                         .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance, BigDecimal myBtcBalance) {
        BigDecimal minFromPossibleBalance = myTotalPossibleBalance.multiply(new BigDecimal("5"));
        BigDecimal minFromActualBtcBalance = myBtcBalance.multiply(new BigDecimal("50"));
        return minFromActualBtcBalance.max(minFromPossibleBalance).intValue();
    }


    private CryptoDto updateCryptoDtoWithVolume(CryptoDto cryptoDto, List<TickerStatistics> tickers) {
        TickerStatistics ticker24hr = CryptoUtils.calculateTicker24hr(tickers, cryptoDto.getSymbolInfo().getSymbol());
        BigDecimal volume = CryptoUtils.calculateVolume(ticker24hr);
        cryptoDto.setTicker24hr(ticker24hr);
        cryptoDto.setVolume(volume);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithCurrentPrice(CryptoDto cryptoDto) {
        String symbol = cryptoDto.getSymbolInfo().getSymbol();
        OrderBook depth = binanceApiService.getDepth(symbol);
        BigDecimal currentPrice = calculateCurrentPrice(depth);
        cryptoDto.setDepth20(depth);
        cryptoDto.setCurrentPrice(currentPrice);
        return cryptoDto;
    }

    private List<CryptoDto> repeatTrading(List<Order> openOrders,
                                          BigDecimal myBtcBalance,
                                          Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                          Map<String, BigDecimal> totalAmounts,
                                          ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Buying big amounts ***** *****");
        Function<OrderDto, Long> countOrdersBySymbol = orderDto -> openOrders.parallelStream()
                                                                             .filter(order -> order.getSymbol().equals(orderDto.getOrder().getSymbol()))
                                                                             .count();
        Function<OrderDto, SymbolInfo> symbolFunction = orderDto -> exchangeInfo.getSymbols()
                                                                                .parallelStream()
                                                                                .filter(symbolInfo -> symbolInfo.getSymbol()
                                                                                                                .equals(orderDto.getOrder().getSymbol()))
                                                                                .findAny()
                                                                                .orElseThrow();
        return openOrders.stream()
                         .map(Order::getSymbol)
                         .distinct()
                         .map(symbol -> openOrders.parallelStream()
                                                  .filter(order -> order.getSymbol().equals(symbol))
                                                  .min(getOrderComparator()))
                         .map(Optional::orElseThrow)
                         .map(repeatTradingService::createOrderDto)
                         .filter(orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                         .map(orderDto -> repeatTradingService.updateOrderDtoWithWaitingTimes(totalAmounts, orderDto))
                         .filter(orderDto -> orderDto.getActualWaitingTime().compareTo(orderDto.getMinWaitingTime()) > 0)
                         .map(repeatTradingService::updateOrderDtoWithPrices)
                         .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(MIN_PROFIT_PERCENT) > 0)
                         .peek(orderDto -> logger.log(orderDto.toString()))
                         .map(orderDto -> repeatTradingService.repeatTrade(symbolFunction.apply(orderDto),
                                                                           orderDto,
                                                                           new BigDecimal(countOrdersBySymbol.apply(orderDto)),
                                                                           cryptoDtosSupplier,
                                                                           totalAmounts,
                                                                           exchangeInfo))
                         .filter(list -> !list.isEmpty())
                         .findFirst()
                         .orElseGet(Collections::emptyList);
    }


    private void initTrading(Supplier<List<CryptoDto>> cryptoDtosSupplier) {
        logger.log("***** ***** Buying small amounts ***** *****");
        cryptoDtosSupplier.get()
                          .stream()
                          .map(initialTradingService::updateCryptoDtoWithLeastMaxAverage)
                          .filter(dto -> dto.getLastThreeMaxAverage().compareTo(dto.getPreviousThreeMaxAverage()) > 0)
                          .map(initialTradingService::updateCryptoDtoWithPrices)
                          .filter(dto -> dto.getPriceToSellPercentage().compareTo(MIN_PROFIT_PERCENT) > 0)
                          .map(initialTradingService::updateCryptoDtoWithSumDiffPerc)
                          .filter(dto -> dto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                          .filter(dto -> dto.getSumDiffsPerc10h().compareTo(new BigDecimal("400")) < 0)
                          .forEach(initialTradingService::buyCrypto);
    }
}