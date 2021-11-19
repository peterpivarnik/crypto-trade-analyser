package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MAX_NUM_ORDERS;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.binance.api.client.domain.market.CandlestickInterval.FIFTEEN_MINUTES;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.dto.OrderDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class BtcBinanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtcBinanceService.class);

    private final BinanceApiRestClient binanceApiRestClient;

    BtcBinanceService() {
        this.binanceApiRestClient = new BinanceApiRestClientImpl("",
                                                                 "");
    }

    @Time
    @Scheduled(cron = "0 */3 * * * ?")
//    @Scheduled(fixedDelay = 100000, initialDelay = 0)
    public void invest() {
        LOGGER.info("******************************************************************************************");
        LOGGER.info("Start of investing.");
        BigDecimal bnbBalance = buyBnB();
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(new OrderRequest(null));
        BigDecimal sumFromOrders = openOrders.parallelStream()
                                             .map(order -> new BigDecimal(order.getPrice())
                                                 .multiply(new BigDecimal(order.getOrigQty())
                                                               .subtract(new BigDecimal(order.getExecutedQty()))))
                                             .reduce(BigDecimal.ZERO, BigDecimal::add);
        LOGGER.info("Number of open orders: " + openOrders.size());
        BigDecimal myBtcBalance = getMyBalance("BTC");
        BigDecimal bnbAmount = bnbBalance.multiply(getCurrentBnbBtcPrice());
        BigDecimal myTotalPossibleBalance = sumFromOrders.add(myBtcBalance).add(bnbAmount);
        LOGGER.info("My possible balance: " + myTotalPossibleBalance);
        BigDecimal myTotalBalance = getMyTotalBalance();
        LOGGER.info("My actual balance: " + myTotalBalance);
        int minOpenOrders = calculateMinNumberOfOrders(myTotalPossibleBalance, myBtcBalance);
        LOGGER.info("Min open orders: " + minOpenOrders);
        rebuyBigOrders(openOrders, myBtcBalance);
        int uniqueOpenOrdersSize = openOrders.parallelStream()
                                             .collect(toMap(Order::getSymbolWithPrice, order -> order, (order1, order2) -> order1))
                                             .values()
                                             .size();
        LOGGER.info("Unique open orders: " + uniqueOpenOrdersSize);
        if (haveBalanceForBuySmallAmounts(getMyBalance("BTC")) && uniqueOpenOrdersSize <= minOpenOrders) {
            buySmallAmounts();
        }
    }

    private void rebuyBigOrders(List<Order> openOrders, BigDecimal myBtcBalance) {
        LOGGER.info("************************************************************");
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        LOGGER.info("Rebuy orders with low amount");
        rebuyBigAmounts(openOrders, myBtcBalance, totalAmounts, createLowAmountOrderInitialFunction());
        LOGGER.info("Rebuy orders with high amount");
        rebuyBigAmounts(openOrders, myBtcBalance, totalAmounts, createHighAmountOrderInitialFunction(openOrders));
    }

    private Map<String, BigDecimal> createTotalAmounts(List<Order> openOrders) {
        return openOrders.stream()
                         .collect(toMap(Order::getSymbol,
                                        order -> new BigDecimal(order.getPrice()).multiply(new BigDecimal(order.getOrigQty())),
                                        BigDecimal::add));
    }

    private BiFunction<Stream<Order>, Map<String, BigDecimal>, Stream<OrderDto>> createLowAmountOrderInitialFunction() {
        return (orderStream, totalAmounts) -> orderStream.map(OrderDto::new)
                                                         .filter(orderDto -> totalAmounts.get(orderDto.getOrder().getSymbol())
                                                                                         .compareTo(new BigDecimal("0.1")) < 0);
    }

    private BiFunction<Stream<Order>, Map<String, BigDecimal>, Stream<OrderDto>> createHighAmountOrderInitialFunction(List<Order> openOrders) {
        return (orderStream, totalAmounts) -> orderStream.map(Order::getSymbol)
                                                         .filter(symbol -> totalAmounts.get(symbol).compareTo(new BigDecimal("0.1")) > 0)
                                                         .distinct()
                                                         .map(symbol -> openOrders.stream()
                                                                                  .filter(order -> order.getSymbol().equals(symbol))
                                                                                  .min(getOrderComparator()))
                                                         .map(Optional::orElseThrow)
                                                         .map(OrderDto::new);
    }

    private Comparator<Order> getOrderComparator() {
        return comparing((Order order) -> new BigDecimal(order.getPrice()))
            .thenComparing(order -> new BigDecimal(order.getOrigQty()))
            .thenComparing(order -> new BigDecimal(order.getTime()));
    }

    private void rebuyBigAmounts(List<Order> openOrders,
                                 BigDecimal myBtcBalance,
                                 Map<String, BigDecimal> totalAmounts,
                                 BiFunction<Stream<Order>, Map<String, BigDecimal>, Stream<OrderDto>> unary) {
        ArrayList<String> failedClientOrderIds = new ArrayList<>();
        boolean tradeDone = false;
        while (openOrders.size() > failedClientOrderIds.size() && !tradeDone) {
            BiFunction<List<Order>, Map<String, BigDecimal>, Stream<OrderDto>> failedOrderIdsFunction =
                (orders, totalAmounts2) -> unary.apply(openOrders.parallelStream()
                                                                 .filter(order -> !failedClientOrderIds.contains(order.getClientOrderId())), totalAmounts);
            Optional<String> failedId = buyBigAmounts(openOrders, myBtcBalance, totalAmounts, failedOrderIdsFunction);
            if (failedId.isPresent()) {
                failedClientOrderIds.add(failedId.get());
            } else {
                tradeDone = true;
            }
        }
    }

    private BigDecimal buyBnB() {
        LOGGER.info("***************");
        LOGGER.info("Buying BNB");
        BigDecimal myBnbBalance = getMyBalance("BNB");
        LOGGER.info("myBnbBalance: " + myBnbBalance);
        if (myBnbBalance.compareTo(new BigDecimal("2")) < 0) {
            BigDecimal currentBnbBtcPrice = getCurrentBnbBtcPrice();
            LOGGER.info("currentBnbBtcPrice: " + currentBnbBtcPrice);
            BigDecimal myBtcBalance = getMyBalance("BTC");
            BigDecimal maxBnbQuantity = myBtcBalance.divide(currentBnbBtcPrice, 8, CEILING);
            LOGGER.info("maxBnbQuantity: " + maxBnbQuantity);
            String quantityToBuy = "1";
            if (maxBnbQuantity.compareTo(ONE) < 0) {
                quantityToBuy = maxBnbQuantity.toPlainString();
            }
            NewOrder buyOrder = new NewOrder("BNBBTC", BUY, MARKET, null, quantityToBuy);
            LOGGER.info("New buyOrder: " + buyOrder);
            binanceApiRestClient.newOrder(buyOrder);
            return getMyBalance("BNB");
        }
        return myBnbBalance;
    }

    private BigDecimal getCurrentBnbBtcPrice() {
        return getDepth("BNBBTC")
            .getAsks()
            .parallelStream()
            .max(comparing(OrderBookEntry::getPrice))
            .map(OrderBookEntry::getPrice)
            .map(BigDecimal::new)
            .orElseThrow(RuntimeException::new);
    }

    private int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance, BigDecimal myBtcBalance) {
        BigDecimal minFromPossibleBalance = myTotalPossibleBalance.multiply(new BigDecimal("10"));
        BigDecimal minFromActualBtcBalance = myBtcBalance.multiply(new BigDecimal("100"));
        return minFromActualBtcBalance.max(minFromPossibleBalance).intValue();
    }

    private void buySmallAmounts() {
        LOGGER.info("******************************");
        LOGGER.info("Buying small amounts");
        List<TickerStatistics> tickers = getAll24hTickers();
        binanceApiRestClient.getExchangeInfo()
                            .getSymbols()
                            .parallelStream()
                            .map(CryptoDto::new)
                            .filter(dto -> dto.getSymbolInfo().getSymbol().endsWith("BTC"))
                            .filter(dto -> !dto.getSymbolInfo().getSymbol().endsWith("BNBBTC"))
                            .filter(dto -> dto.getSymbolInfo().getStatus() == SymbolStatus.TRADING)
                            .peek(dto -> dto.setThreeMonthsCandleStickData(getCandleStickData(dto, DAILY, 90)))
                            .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
                            .peek(dto -> dto.calculateTicker24hr(tickers))
                            .peek(CryptoDto::calculateVolume)
                            .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                            .peek(dto -> dto.setDepth20(getDepth(dto.getSymbolInfo().getSymbol())))
                            .peek(CryptoDto::calculateCurrentPrice)
                            .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                            .peek(dto -> dto.setFifteenMinutesCandleStickData(getCandleStickData(dto, FIFTEEN_MINUTES, 96)))
                            .peek(CryptoDto::calculateLastThreeMaxAverage)
                            .peek(CryptoDto::calculatePreviousThreeMaxAverage)
                            .filter(dto -> dto.getLastThreeMaxAverage().compareTo(dto.getPreviousThreeMaxAverage()) > 0)
                            .peek(CryptoDto::calculateSumDiffsPercent)
                            .peek(CryptoDto::calculateSumDiffsPercent10h)
                            .peek(CryptoDto::calculatePriceToSell)
                            .peek(CryptoDto::calculatePriceToSellPercentage)
                            .filter(dto -> dto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                            .peek(CryptoDto::calculateWeight)
                            .filter(dto -> dto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                            .filter(dto -> dto.getSumDiffsPerc10h().compareTo(new BigDecimal("400")) < 0)
                            .limit(20)
                            .forEach(this::tradeCrypto);
    }

    private List<TickerStatistics> getAll24hTickers() {
        return binanceApiRestClient.getAll24HrPriceStatistics();
    }

    private OrderBook getDepth(String symbol) {
        return binanceApiRestClient.getOrderBook(symbol, 20);
    }

    private List<Candlestick> getCandleStickData(CryptoDto cryptoDto, CandlestickInterval interval, Integer limit) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return binanceApiRestClient.getCandlestickBars(symbol, interval, limit, null, null);
    }

    private synchronized void tradeCrypto(CryptoDto crypto) {
        // 1. get balance on account
        LOGGER.info("Trading crypto " + crypto.getSymbolInfo().getSymbol());
        String symbol = crypto.getSymbolInfo().getSymbol();
        BigDecimal myBtcBalance = getMyBalance("BTC");

        // 2. get max possible buy
        OrderBook orderBook = binanceApiRestClient.getOrderBook(symbol, 20);
        OrderBookEntry orderBookEntry = orderBook.getAsks()
                                                 .parallelStream()
                                                 .min(comparing(OrderBookEntry::getPrice))
                                                 .orElseThrow(RuntimeException::new);
        LOGGER.info("OrderBookEntry: " + orderBookEntry);

        // 3. calculate amount to buy
        if (isStillValid(crypto, orderBookEntry) && haveBalanceForBuySmallAmounts(myBtcBalance)) {
            BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
            BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, CEILING);
            BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
            BigDecimal roundedMyQuatity = round(crypto.getSymbolInfo(), min, LOT_SIZE, SymbolFilter::getMinQty);
            BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(crypto.getSymbolInfo(), MIN_NOTIONAL, SymbolFilter::getMinNotional);
            if (roundedMyQuatity.multiply(new BigDecimal(orderBookEntry.getPrice())).compareTo(minNotionalFromMinNotionalFilter) < 0) {
                LOGGER.info("Skip trading due to low trade amount: quantity: " + roundedMyQuatity + ", price: " + orderBookEntry.getPrice());
                return;
            }

            // 4. buy
            NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, roundedMyQuatity.toPlainString());
            LOGGER.info("BuyOrder: " + buyOrder);
            NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(buyOrder);
            // 5. place bid
            if (newOrderResponse.getStatus() == OrderStatus.FILLED) {
                try {
                    placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), roundedMyQuatity);
                } catch (Exception e) {
                    LOGGER.info("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
                    sleep(61000);
                    placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), roundedMyQuatity);
                }
            }
        }
    }

    private BigDecimal getMyBalance(String symbol) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
                                      .parallelStream()
                                      .filter(balance -> balance.getAsset().equals(symbol))
                                      .map(AssetBalance::getFree)
                                      .map(BigDecimal::new)
                                      .findFirst()
                                      .orElse(BigDecimal.ZERO);
        LOGGER.info("My balance in currency: " + symbol + ", is: " + myBalance);
        return myBalance;
    }

    private BigDecimal getMyTotalBalance() {
        return binanceApiRestClient.getAccount()
                                   .getBalances()
                                   .parallelStream()
                                   .map(this::mapToAssetAndBalance)
                                   .filter(pair -> pair.getLeft().compareTo(BigDecimal.ZERO) > 0)
                                   .map(this::mapToBtcBalance)
                                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Pair<BigDecimal, String> mapToAssetAndBalance(AssetBalance assetBalance) {
        return Pair.of(new BigDecimal(assetBalance.getFree()).add(new BigDecimal(assetBalance.getLocked())), assetBalance.getAsset());
    }

    private BigDecimal mapToBtcBalance(Pair<BigDecimal, String> pair) {
        if (pair.getRight().equals("BTC")) {
            return pair.getLeft();
        } else {
            try {
                BigDecimal price = getDepth(pair.getRight() + "BTC")
                    .getBids()
                    .parallelStream()
                    .map(OrderBookEntry::getPrice)
                    .map(BigDecimal::new)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
                return price.multiply(pair.getLeft());
            } catch (BinanceApiException e) {
                return BigDecimal.ZERO;
            }
        }
    }

    private boolean isStillValid(CryptoDto crypto, OrderBookEntry orderBookEntry) {
        return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
    }

    private boolean haveBalanceForBuySmallAmounts(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
    }

    private Optional<String> buyBigAmounts(List<Order> openOrders,
                                           BigDecimal myBtcBalance,
                                           Map<String, BigDecimal> totalAmounts,
                                           BiFunction<List<Order>, Map<String, BigDecimal>, Stream<OrderDto>> failedOrderIdsFunction) {
        Function<OrderDto, Long> countOrdersBySymbol = orderDto -> openOrders.parallelStream()
                                                                             .filter(order -> order.getSymbol().equals(orderDto.getOrder().getSymbol()))
                                                                             .count();
        return failedOrderIdsFunction.apply(openOrders, totalAmounts)
                                     .peek(OrderDto::calculateOrderBtcAmount)
                                     .filter(orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                                     .peek(orderDto -> orderDto.calculateMinWaitingTime(totalAmounts.get(orderDto.getOrder().getSymbol())))
                                     .peek(OrderDto::calculateActualWaitingTime)
                                     .filter(orderDto -> orderDto.getActualWaitingTime().compareTo(orderDto.getMinWaitingTime()) > 0)
                                     .peek(orderDto -> orderDto.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol())))
                                     .peek(OrderDto::calculatePriceToSellWithoutProfit)
                                     .peek(OrderDto::calculatePriceToSell)
                                     .peek(OrderDto::calculatePriceToSellPercentage)
                                     .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                                     .peek(orderDto -> LOGGER.info(orderDto.print()))
                                     .max(comparing(OrderDto::getPriceToSellPercentage))
                                     .flatMap(orderDto -> rebuy(orderDto, new BigDecimal(countOrdersBySymbol.apply(orderDto))));
    }

    private Optional<String> rebuy(OrderDto orderDto, BigDecimal symbolOpenOrders) {
        return binanceApiRestClient.getExchangeInfo()
                                   .getSymbols()
                                   .parallelStream()
                                   .filter(symbolInfo -> symbolInfo.getSymbol().equals(orderDto.getOrder().getSymbol()))
                                   .findAny()
                                   .flatMap(symbolInfo -> rebuyOrder(symbolInfo, orderDto, symbolOpenOrders));
    }

    private Optional<String> rebuyOrder(SymbolInfo symbolInfo, OrderDto orderDto, BigDecimal currentNumberOfOpenOrdersBySymbol) {
        LOGGER.info("Rebuying: symbol=" + symbolInfo.getSymbol());
        LOGGER.info("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        try {
            // 1. cancel existing order
            CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(orderDto.getOrder().getSymbol(), orderDto.getOrder().getClientOrderId());
            LOGGER.info("New cancelOrderRequest" + cancelOrderRequest);
            binanceApiRestClient.cancelOrder(cancelOrderRequest);
        } catch (BinanceApiException e) {
            LOGGER.info("Catching exception for orderId: " + orderDto.getOrder().getClientOrderId());
            return Optional.of(orderDto.getOrder().getClientOrderId());
        }
        // 2. buy
        BigDecimal totalBtcAmountToRebuy = orderDto.getOrderBtcAmount();
        BigDecimal myQuantity = totalBtcAmountToRebuy.divide(orderDto.getOrderPrice(), 8, CEILING);
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
        BigDecimal roundedQuantity = round(symbolInfo, myQuantityToBuy, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal quantity = doubleIfNecessary(roundedQuantity, orderDto, symbolInfo);
        NewOrder buyOrder = new NewOrder(orderDto.getOrder().getSymbol(), BUY, MARKET, null, quantity.toPlainString());
        LOGGER.info("New buyOrder: " + buyOrder);
        binanceApiRestClient.newOrder(buyOrder);

        // 3. create new order
        BigDecimal originalQuantity = new BigDecimal(orderDto.getOrder().getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(orderDto.getOrder().getExecutedQty());
        BigDecimal quantityToSell = originalQuantity.subtract(executedQuantity);
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);
        if ((orderDto.getOrderBtcAmount().compareTo(new BigDecimal("0.01")) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
            LOGGER.info("Splitting amount: " + orderDto.getOrderBtcAmount());
            BigDecimal tenthOfCompleteQuantity = calculateTenthPartOfQuantity(completeQuantityToSell, symbolInfo);
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), tenthOfCompleteQuantity);
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), tenthOfCompleteQuantity.multiply(new BigDecimal("2")));
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), tenthOfCompleteQuantity.multiply(new BigDecimal("3")));
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), getRestOfCancelledAmount(symbolInfo));
        } else {
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), completeQuantityToSell);
        }
        return Optional.empty();
    }

    private BigDecimal calculateTenthPartOfQuantity(BigDecimal completeQuantityToSell, SymbolInfo symbolInfo) {
        BigDecimal dividedTenthPart = completeQuantityToSell.divide(TEN, 8, CEILING);
        LOGGER.info("dividedTenthPart: " + dividedTenthPart);
        BigDecimal roundedTenthPart = round(symbolInfo, dividedTenthPart, LOT_SIZE, SymbolFilter::getMinQty);
        LOGGER.info("roundedTenthPart: " + roundedTenthPart);
        return roundedTenthPart;
    }

    private BigDecimal getRestOfCancelledAmount(SymbolInfo symbolInfo) {
        String symbolFromSymbolInfo = getSymbolFromSymbolInfo(symbolInfo);
        LOGGER.info("symbolFromSymbolInfo: " + symbolFromSymbolInfo);
        BigDecimal myBalance = getMyBalance(symbolFromSymbolInfo);
        LOGGER.info("myBalance: " + myBalance);
        return myBalance;
    }

    private BigDecimal doubleIfNecessary(BigDecimal roundedQuantity, OrderDto orderDto, SymbolInfo symbolInfo) {
        BigDecimal minNotional = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        if (roundedQuantity.multiply(orderDto.getOrderPrice()).compareTo(minNotional) < 0) {
            return roundedQuantity.multiply(new BigDecimal("2"));
        }
        return roundedQuantity;
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        LOGGER.info("Place new order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String currencyShortcut = getSymbolFromSymbolInfo(symbolInfo);
        BigDecimal myBalance = waitUntilHaveBalance(currencyShortcut, quantity);
        BigDecimal roundedBidQuantity = round(symbolInfo, myBalance, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal roundedPriceToSell = round(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, roundedBidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        LOGGER.info("My new sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }

    private String getSymbolFromSymbolInfo(SymbolInfo symbolInfo) {
        return symbolInfo.getSymbol().substring(0, symbolInfo.getSymbol().length() - 3);
    }

    private BigDecimal waitUntilHaveBalance(String symbol, BigDecimal quantity) {
        BigDecimal myBalance = getMyBalance(symbol);
        if (myBalance.compareTo(quantity) >= 0) {
            return quantity;
        } else {
            sleep(500);
            return waitUntilHaveBalance(symbol, quantity);
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LOGGER.error("Error during sleeping");
        }
    }

    private BigDecimal round(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder);
    }

    private BigDecimal getValueFromFilter(SymbolInfo symbolInfo, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        return symbolInfo.getFilters()
                         .parallelStream()
                         .filter(filter -> filter.getFilterType().equals(filterType))
                         .map(symbolFilterFunction)
                         .map(BigDecimal::new)
                         .findAny()
                         .orElseThrow(RuntimeException::new);
    }
}
