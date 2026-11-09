package com.psw.cta;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static java.lang.String.format;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.FLOOR;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.processor.LocalTradeProcessor;
import com.psw.cta.processor.MainTradeProcessor;
import com.psw.cta.service.BnbTradeService;
import com.psw.cta.service.OrphanBalanceService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Main service for cryptocurrency trading operations. This class handles trading initialization,
 * management of orders, balance tracking, and execution of trading strategies. It supports both
 * local and AWS Lambda environments.
 */
public class CryptoTrader {

    private final BnbTradeService bnbTradeService;
    private final BinanceAdapter binanceAdapter;
    private final LambdaLogger logger;
    private final MainTradeProcessor tradeProcessor;
    private final OrphanBalanceService orphanBalanceService;

    /**
     * Constructor of {@link CryptoTrader} for local environment.
     *
     * @param binanceAdapter    Service for interacting with Binance API
     * @param bnbTradeService Processor for BNB-specific trading operations
     * @param tradeProcessor       Processor for handling local trading operations
     * @param orphanBalanceService Checker of free balances without sell order
     * @param logger               Logger for recording trading operations and events
     */
    public CryptoTrader(BinanceAdapter binanceAdapter,
                        BnbTradeService bnbTradeService,
                        LocalTradeProcessor tradeProcessor,
                        OrphanBalanceService orphanBalanceService,
                        LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
        this.bnbTradeService = bnbTradeService;
        this.tradeProcessor = tradeProcessor;
        this.orphanBalanceService = orphanBalanceService;
        this.logger = logger;
    }

    /**
     * Constructor of {@link CryptoTrader} for AWS environment.
     *
     * @param binanceAdapter    Service for interacting with Binance API
     * @param bnbTradeService Processor for BNB-specific trading operations
     * @param tradeProcessor       Processor for handling Lambda trading operations
     * @param orphanBalanceService Checker of free balances without sell order
     * @param logger               Logger for recording trading operations and events
     */
    public CryptoTrader(BinanceAdapter binanceAdapter,
                        BnbTradeService bnbTradeService,
                        LambdaTradeProcessor tradeProcessor,
                        OrphanBalanceService orphanBalanceService,
                        LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
        this.bnbTradeService = bnbTradeService;
        this.tradeProcessor = tradeProcessor;
        this.orphanBalanceService = orphanBalanceService;
        this.logger = logger;
    }

    /**
     * Start trading.
     */
    public void startTrading() {
        logger.log("***** ***** Start of trading ***** *****");
        LocalDateTime start = LocalDateTime.now();
        logger.log("Crypto trader with version " + getVersion() + " started at " + start + ".");
        List<Order> openOrders = binanceAdapter.getOpenOrders();
        logger.log("Number of open orders: " + openOrders.size());
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        logTotalAmounts(totalAmounts);
        BigDecimal ordersAmount = totalAmounts.values()
                                              .stream()
                                              .reduce(ZERO, BigDecimal::add);
        logger.log("ordersAmount: " + ordersAmount);
        BigDecimal myBtcBalance = binanceAdapter.getMyBalance(ASSET_BTC);
        BigDecimal ordersAndBtcAmount = ordersAmount.add(myBtcBalance);
        logger.log("ordersAndBtcAmount: " + ordersAndBtcAmount.stripTrailingZeros());
        ExchangeInfo exchangeInfo = binanceAdapter.getExchangeInfo();
        BigDecimal currentBnbBtcPrice = binanceAdapter.getCurrentPrice(SYMBOL_BNB_BTC);
        logger.log("currentBnbBtcPrice: " + currentBnbBtcPrice);
        SymbolInfo bnbSymbolInfo = exchangeInfo.getSymbolInfo(SYMBOL_BNB_BTC);
        BigDecimal bnbBalance = bnbTradeService.buyBnB(currentBnbBtcPrice, bnbSymbolInfo);
        BigDecimal bnbAmount = bnbBalance.multiply(currentBnbBtcPrice);
        BigDecimal totalAmount = ordersAndBtcAmount.add(bnbAmount);
        logger.log("totalAmount: " + totalAmount.stripTrailingZeros());
        int minOpenOrders = calculateMinNumberOfOrders(myBtcBalance);
        logger.log("Min open orders: " + minOpenOrders);
        long uniqueOpenOrdersSize = openOrders.parallelStream()
                                              .map(Order::getSymbol)
                                              .distinct()
                                              .count();
        logger.log("Unique open orders: " + uniqueOpenOrdersSize);
        BigDecimal actualBalance = binanceAdapter.getMyActualBalance();
        logger.log("actualBalance: " + actualBalance.stripTrailingZeros());
        tradeProcessor.trade(openOrders,
                             totalAmounts,
                             myBtcBalance,
                             actualBalance,
                             exchangeInfo,
                             uniqueOpenOrdersSize,
                             minOpenOrders);
        List<Order> newOpenOrders = binanceAdapter.getOpenOrders();
        Map<String, BigDecimal> newTotalAmounts = createTotalAmounts(newOpenOrders);
        logTotalAmounts(newTotalAmounts);
        List<String> problems = new ArrayList<>();
        checkOldAndNewAmount(ordersAndBtcAmount, newOpenOrders).ifPresent(problems::add);
        List<String> orphanBalances = orphanBalanceService.findOrphanBalances(exchangeInfo);
        if (!orphanBalances.isEmpty()) {
            problems.add("Orphan balances: " + String.join("; ", orphanBalances) + ".");
        }
        LocalDateTime end = LocalDateTime.now();
        logger.log("Finished trading at " + end + ".");
        Duration duration = Duration.between(start, end);
        logger.log("Execution took: "
                   + duration.toSeconds()
                   + " seconds, and "
                   + duration.toMillisPart()
                   + " milliseconds.");
        if (!problems.isEmpty()) {
            throw new BinanceApiException(String.join(" ", problems));
        }
    }

    private String getVersion() {
        final Properties properties = new Properties();
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream("app.properties");
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            logger.log("Failed to load app.properties");
            throw new CryptoTraderException(e);
        }
        return properties.getProperty("application.version");
    }

    private Map<String, BigDecimal> createTotalAmounts(List<Order> openOrders) {
        return openOrders.stream()
                         .collect(toMap(Order::getSymbol,
                                        order -> new BigDecimal(order.getPrice())
                                            .multiply(getQuantity(order))
                                            .setScale(8, FLOOR),
                                        BigDecimal::add))
                         .entrySet()
                         .stream()
                         .sorted((c1, c2) -> c2.getValue().compareTo(c1.getValue()))
                         .collect(toMap(Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (e1, e2) -> e1,
                                        LinkedHashMap::new));
    }

    private void logTotalAmounts(Map<String, BigDecimal> totalAmounts) {
        logger.log("totalAmounts: ");
        List<Map.Entry<String, BigDecimal>> entryList = totalAmounts.entrySet()
                                                                    .stream()
                                                                    .toList();
        int numberOfTens = entryList.size() / 10;
        List<List<Map.Entry<String, BigDecimal>>> listOfLists = new ArrayList<>();
        for (int i = 0; i < numberOfTens; i++) {
            listOfLists.add(entryList.subList(i * 10, (i + 1) * 10));
        }
        listOfLists.add(entryList.subList(numberOfTens * 10, entryList.size()));
        listOfLists.forEach(subList -> {
            String sublistString = subList.stream()
                                          .map(stringBigDecimalEntry -> format("%10s=%-11s",
                                                                               stringBigDecimalEntry.getKey(),
                                                                               stringBigDecimalEntry.getValue()))
                                          .collect(Collectors.joining());
            logger.log(sublistString);
        });
    }

    private int calculateMinNumberOfOrders(BigDecimal myBtcBalance) {
        return myBtcBalance.multiply(new BigDecimal("50")).intValue();
    }

    private Optional<String> checkOldAndNewAmount(BigDecimal ordersAndBtcAmount, List<Order> newOpenOrders) {
        Map<String, BigDecimal> newTotalAmounts = createTotalAmounts(newOpenOrders);
        BigDecimal newOrdersAmount = newTotalAmounts.values()
                                                    .stream()
                                                    .reduce(ZERO, BigDecimal::add);
        BigDecimal myNewBtcBalance = binanceAdapter.getMyBalance(ASSET_BTC);
        BigDecimal newOrdersAndBtcAmount = newOrdersAmount.add(myNewBtcBalance);
        logger.log("newOrdersAndBtcAmount: " + newOrdersAndBtcAmount);
        BigDecimal ordersAndBtcAmountDifference = newOrdersAndBtcAmount.subtract(ordersAndBtcAmount);

        if (ordersAndBtcAmountDifference.compareTo(ZERO) > 0) {
            logger.log("ordersAndBtcAmountDifference: " + ordersAndBtcAmountDifference);
        } else if (ordersAndBtcAmountDifference.compareTo(ZERO) < 0) {
            return Optional.of("New amount lower than before trading! Old amount : "
                               + ordersAndBtcAmount
                               + ". New amount: "
                               + newOrdersAndBtcAmount
                               + ". Difference: "
                               + ordersAndBtcAmountDifference
                               + ".");
        }
        return Optional.empty();
    }

    private BigDecimal getQuantity(Order order) {
        return new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
    }
}