package com.psw.cta.service;

import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.binance.api.client.domain.market.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.utils.CommonUtils.calculateMinNumberOfOrders;
import static com.psw.cta.utils.CommonUtils.createTotalAmounts;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENT;
import static com.psw.cta.utils.CryptoBuilder.withCurrentPrice;
import static com.psw.cta.utils.CryptoBuilder.withLeastMaxAverage;
import static com.psw.cta.utils.CryptoBuilder.withVolume;
import static com.psw.cta.utils.OrderWrapperBuilder.withPrices;
import static com.psw.cta.utils.OrderWrapperBuilder.withWaitingTimes;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.Constants;
import com.psw.cta.utils.CryptoBuilder;
import com.psw.cta.utils.OrderWrapperBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TradingService {

    private final InitialTradingService initialTradingService;
    private final RepeatTradingService repeatTradingService;
    private final DiversifyService diversifyService;
    private final BnbService bnbService;
    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public TradingService(InitialTradingService initialTradingService,
                          RepeatTradingService repeatTradingService,
                          DiversifyService diversifyService,
                          BnbService bnbService,
                          BinanceApiService binanceApiService,
                          LambdaLogger logger) {
        this.initialTradingService = initialTradingService;
        this.repeatTradingService = repeatTradingService;
        this.diversifyService = diversifyService;
        this.bnbService = bnbService;
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public void startTrading() {
        logger.log("***** ***** Start of trading ***** *****");
        BigDecimal bnbBalance = bnbService.buyBnB();
        List<Order> openOrders = binanceApiService.getOpenOrders();
        logger.log("Number of open orders: " + openOrders.size());
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        logger.log("totalAmounts: " + totalAmounts);
        BigDecimal ordersAmount = totalAmounts.values()
                                              .stream()
                                              .reduce(ZERO, BigDecimal::add);
        logger.log("ordersAmount: " + ordersAmount);
        BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
        BigDecimal ordersAndBtcAmount = ordersAmount.add(myBtcBalance);
        logger.log("ordersAndBtcAmount: " + ordersAndBtcAmount);
        BigDecimal bnbAmount = bnbBalance.multiply(bnbService.getCurrentBnbBtcPrice());
        BigDecimal totalAmount = ordersAndBtcAmount.add(bnbAmount);
        logger.log("totalAmount: " + totalAmount);
        int minOpenOrders = calculateMinNumberOfOrders(totalAmount, myBtcBalance);
        logger.log("Min open orders: " + minOpenOrders);

        ExchangeInfo exchangeInfo = binanceApiService.getExchangeInfo();
        int uniqueOpenOrdersSize = openOrders.parallelStream()
                                             .collect(toMap(Order::getSymbolWithPrice, order -> order, (order1, order2) -> order1))
                                             .values()
                                             .size();
        logger.log("Unique open orders: " + uniqueOpenOrdersSize);
        if (canHaveMoreOrders(minOpenOrders, uniqueOpenOrdersSize)) {
            expandOrders(openOrders, myBtcBalance, totalAmounts, exchangeInfo);
        } else {
            repeatTrading(openOrders, myBtcBalance, totalAmounts, exchangeInfo);
        }
        logger.log("Get actual balance");
        BigDecimal actualBalance = binanceApiService.getMyActualBalance();
        logger.log("actualBalance: " + actualBalance);
    }

    private void repeatTrading(List<Order> openOrders,
                               BigDecimal myBtcBalance,
                               Map<String, BigDecimal> totalAmounts,
                               ExchangeInfo exchangeInfo) {
        Function<OrderWrapper, SymbolInfo> symbolFunction = orderWrapper -> exchangeInfo.getSymbols()
                                                                                        .parallelStream()
                                                                                        .filter(symbolInfo -> symbolInfo.getSymbol()
                                                                                                                        .equals(orderWrapper.getOrder()
                                                                                                                                            .getSymbol()))
                                                                                        .findAny()
                                                                                        .orElseThrow();
        List<OrderWrapper> wrappers = getOrderWrappers(openOrders, myBtcBalance, totalAmounts, exchangeInfo);
        wrappers.stream()
                .filter(orderWrapper -> orderWrapper.getActualWaitingTime().compareTo(orderWrapper.getMinWaitingTime()) > 0)
                .forEach(orderWrapper -> repeatTradingService.rebuySingleOrder(symbolFunction.apply(orderWrapper), orderWrapper));
    }

    private List<OrderWrapper> getOrderWrappers(List<Order> openOrders,
                                                BigDecimal myBtcBalance,
                                                Map<String, BigDecimal> totalAmounts,
                                                ExchangeInfo exchangeInfo) {
        return openOrders.stream()
                         .map(Order::getSymbol)
                         .distinct()
                         .map(symbol -> openOrders.parallelStream()
                                                  .filter(order -> order.getSymbol().equals(symbol))
                                                  .min(getOrderComparator()))
                         .map(Optional::orElseThrow)
                         .map(OrderWrapperBuilder::build)
                         .filter(orderWrapper -> orderWrapper.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                         .map(orderWrapper -> withWaitingTimes(totalAmounts, orderWrapper))
                         .map(orderWrapper -> withPrices(orderWrapper,
                                                         binanceApiService.getOrderBook(orderWrapper.getOrder().getSymbol()),
                                                         exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol())))

                         .filter(orderWrapper -> orderWrapper.getPriceToSellPercentage().compareTo(MIN_PROFIT_PERCENT) > 0)
                         .peek(orderWrapper -> logger.log(orderWrapper.toString()))
                         .collect(Collectors.toList());
    }

    private boolean canHaveMoreOrders(int minOpenOrders, int uniqueOpenOrdersSize) {
        return uniqueOpenOrdersSize <= minOpenOrders;
    }

    private void expandOrders(List<Order> openOrders,
                              BigDecimal myBtcBalance,
                              Map<String, BigDecimal> totalAmounts,
                              ExchangeInfo exchangeInfo) {
        List<OrderWrapper> orderWrappers = getOrderWrappers(openOrders, myBtcBalance, totalAmounts, exchangeInfo);
        diversify(totalAmounts, exchangeInfo, orderWrappers);
        BigDecimal myBalance = binanceApiService.getMyBalance(ASSET_BTC);
        if (haveBalanceForInitialTrading(myBalance)) {
            initTrading(() -> getCryptos(exchangeInfo));
        }
    }

    private void diversify(Map<String, BigDecimal> totalAmounts, ExchangeInfo exchangeInfo, List<OrderWrapper> orderWrappers) {
        orderWrappers.stream()
                     .max(comparing(OrderWrapper::getOrderBtcAmount))
                     .ifPresent((orderWrapper) -> diversifyService.diversify(orderWrapper,
                                                                             () -> getCryptos(exchangeInfo),
                                                                             totalAmounts,
                                                                             exchangeInfo));
    }

    private List<Crypto> getCryptos(ExchangeInfo exchangeInfo) {
        logger.log("Sleep for 1 minute before get all cryptos");
        sleep(1000 * 60, logger);
        logger.log("Get all cryptos");
        List<TickerStatistics> tickers = binanceApiService.getAll24hTickers();
        List<Crypto> cryptos = exchangeInfo.getSymbols()
                                           .parallelStream()
                                           .map(CryptoBuilder::build)
                                           .filter(crypto -> crypto.getSymbolInfo().getSymbol().endsWith(ASSET_BTC))
                                           .filter(crypto -> !crypto.getSymbolInfo().getSymbol().endsWith(Constants.SYMBOL_BNB_BTC))
                                           .filter(crypto -> crypto.getSymbolInfo().getStatus() == SymbolStatus.TRADING)
                                           .map(crypto -> withVolume(crypto, tickers))
                                           .filter(crypto -> crypto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                           .map(crypto -> crypto.setThreeMonthsCandleStickData(binanceApiService.getCandleStickData(crypto, DAILY, 90)))
                                           .filter(crypto -> crypto.getThreeMonthsCandleStickData().size() >= 90)
                                           .map(crypto -> withCurrentPrice(crypto, binanceApiService.getOrderBook(crypto.getSymbolInfo().getSymbol())))
                                           .filter(crypto -> crypto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                                           .collect(Collectors.toList());
        logger.log("Cryptos count: " + cryptos.size());
        return cryptos;
    }

    private void initTrading(Supplier<List<Crypto>> cryptosSupplier) {
        logger.log("***** ***** Initial trading ***** *****");
        cryptosSupplier.get()
                       .stream()
                       .map(crypto -> withLeastMaxAverage(crypto, binanceApiService.getCandleStickData(crypto, FIFTEEN_MINUTES, 96)))
                       .filter(crypto -> crypto.getLastThreeHighAverage().compareTo(crypto.getPreviousThreeHighAverage()) > 0)
                       .map(CryptoBuilder::withPrices)
                       .filter(crypto -> crypto.getPriceToSellPercentage().compareTo(MIN_PROFIT_PERCENT) > 0)
                       .map(CryptoBuilder::withSumDiffPerc)
                       .filter(crypto -> crypto.getSumPercentageDifferences1h().compareTo(new BigDecimal("4")) < 0)
                       .filter(crypto -> crypto.getSumPercentageDifferences10h().compareTo(new BigDecimal("400")) < 0)
                       .forEach(initialTradingService::buyCrypto);
    }
}