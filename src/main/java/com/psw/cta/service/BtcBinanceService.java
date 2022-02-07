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
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CryptoUtils.calculateCurrentPrice;
import static com.psw.cta.utils.CryptoUtils.calculateLastThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSellPercentage;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent10h;
import static com.psw.cta.utils.Fibonacci.FIBONACCI_SEQUENCE;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSellWithoutProfit;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.DOWN;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.general.ExchangeInfo;
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
import com.psw.cta.dto.CryptoDto;
import com.psw.cta.dto.OrderDto;
import com.psw.cta.utils.CryptoUtils;
import com.psw.cta.utils.OrderUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class BtcBinanceService {

    private final BinanceApiRestClient binanceApiRestClient;
    private final LambdaLogger logger;

    public BtcBinanceService(BinanceApiRestClient binanceApiRestClient, LambdaLogger logger) {
        this.binanceApiRestClient = binanceApiRestClient;
        this.logger = logger;
    }

    public void invest() {
        logger.log("***** ***** Start of investing ***** *****");
        BigDecimal bnbBalance = buyBnB();
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(new OrderRequest(null));
        BigDecimal sumFromOrders = openOrders.parallelStream()
                                             .map(order -> new BigDecimal(order.getPrice())
                                                 .multiply(new BigDecimal(order.getOrigQty())
                                                               .subtract(new BigDecimal(order.getExecutedQty()))))
                                             .reduce(BigDecimal.ZERO, BigDecimal::add);
        logger.log("Number of open orders: " + openOrders.size());
        BigDecimal myBtcBalance = getMyBalance("BTC");
        BigDecimal bnbAmount = bnbBalance.multiply(getCurrentBnbBtcPrice());
        BigDecimal myTotalPossibleBalance = sumFromOrders.add(myBtcBalance).add(bnbAmount);
        logger.log("My possible balance: " + myTotalPossibleBalance);
        BigDecimal myTotalBalance = getMyTotalBalance();
        logger.log("My actual balance: " + myTotalBalance);
        int minOpenOrders = calculateMinNumberOfOrders(myTotalPossibleBalance, myBtcBalance);
        logger.log("Min open orders: " + minOpenOrders);
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        logger.log("totalAmounts: " + totalAmounts);

        ExchangeInfo exchangeInfo = binanceApiRestClient.getExchangeInfo();
        List<CryptoDto> cryptoDtos = buyBigAmounts(openOrders, myBtcBalance, () -> getCryptoDtos(exchangeInfo), totalAmounts, exchangeInfo);
        int uniqueOpenOrdersSize = openOrders.parallelStream()
                                             .collect(toMap(Order::getSymbolWithPrice, order -> order, (order1, order2) -> order1))
                                             .values()
                                             .size();
        logger.log("Unique open orders: " + uniqueOpenOrdersSize);
        if (haveBalanceForBuySmallAmounts(getMyBalance("BTC")) && uniqueOpenOrdersSize <= minOpenOrders) {
            buySmallAmounts(() -> getCryptoDtos(cryptoDtos, exchangeInfo));
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
        sleep(1000 * 60);
        List<TickerStatistics> tickers = getAll24hTickers();
        List<CryptoDto> cryptoDtos = exchangeInfo.getSymbols()
                                                 .parallelStream()
                                                 .map(CryptoDto::new)
                                                 .filter(dto -> dto.getSymbolInfo().getSymbol().endsWith("BTC"))
                                                 .filter(dto -> !dto.getSymbolInfo().getSymbol().endsWith("BNBBTC"))
                                                 .filter(dto -> dto.getSymbolInfo().getStatus() == SymbolStatus.TRADING)
                                                 .map(cryptoDto -> updateCryptoDtoWithVolume(cryptoDto, tickers))
                                                 .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                                 .map(dto -> dto.setThreeMonthsCandleStickData(getCandleStickData(dto, DAILY, 90)))
                                                 .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
                                                 .map(this::updateCryptoDtoWithCurrentPrice)
                                                 .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                                                 .collect(Collectors.toList());
        logger.log("Cryptos count: " + cryptoDtos.size());
        return cryptoDtos;
    }

    private List<CryptoDto> diversify(OrderDto orderToCancel,
                                      Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                      Map<String, BigDecimal> totalAmounts,
                                      ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Diversifying amounts ***** *****");

        // 1. cancel existing order
        logger.log("orderToCancel: " + orderToCancel);
        cancelRequest(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = getQuantityFromOrder(orderToCancel);
        logger.log("currentQuantity: " + currentQuantity);
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        sellMarketOrder(symbolInfoOfSellOrder, currentQuantity);

        List<CryptoDto> cryptoDtos = cryptoDtosSupplier.get();
        List<CryptoDto> cryptosToBuy = getCryptoToBuy(cryptoDtos, totalAmounts);
        cryptosToBuy.forEach(cryptoToBuy -> {
            // 3. buy
            logger.log("cryptoToBuy: " + cryptoToBuy);
            SymbolInfo symbolInfo = cryptoToBuy.getSymbolInfo();
            BigDecimal cryptoToBuyCurrentPrice = cryptoToBuy.getCurrentPrice();
            logger.log("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);
            BigDecimal currentBtcAmount = currentQuantity.multiply(orderToCancel.getCurrentPrice())
                                                         .divide(new BigDecimal(cryptosToBuy.size()), 8, CEILING);
            logger.log("currentBtcAmount: " + currentBtcAmount);
            BigDecimal boughtQuantity = buy(symbolInfo, currentBtcAmount, cryptoToBuyCurrentPrice);
            logger.log("boughtQuantity: " + boughtQuantity);

            // 4. place sell order
            BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                                     .multiply(new BigDecimal("1.01"))
                                                                     .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
            logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
            placeSellOrders(symbolInfo, finalPriceWithProfit, boughtQuantity);
        });

        return cryptoDtos;
    }

    private BigDecimal getQuantityFromOrder(OrderDto orderToCancel) {
        BigDecimal originalQuantity = new BigDecimal(orderToCancel.getOrder().getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(orderToCancel.getOrder().getExecutedQty());
        return originalQuantity.subtract(executedQuantity);
    }

    private void sellMarketOrder(SymbolInfo symbolInfo, BigDecimal quantity) {
        logger.log("Sell order: " + symbolInfo.getSymbol() + ", quantity=" + quantity);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal myBalance = waitUntilHaveBalance(asset, quantity);
        BigDecimal roundedBidQuantity = roundDown(symbolInfo, myBalance, LOT_SIZE, SymbolFilter::getMinQty);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, MARKET, null, roundedBidQuantity.toPlainString());
        logger.log("My new sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }

    private List<CryptoDto> getCryptoToBuy(List<CryptoDto> cryptoDtos, Map<String, BigDecimal> totalAmounts) {
        List<String> bigOrderKeys = totalAmounts.entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue().compareTo(new BigDecimal("0.005")) > 0)
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toList());

        return cryptoDtos.stream()
                         .filter(dto -> !bigOrderKeys.contains(dto.getSymbolInfo().getSymbol()))
                         .map(this::updateCryptoDtoWithSlopeData)
                         .filter(cryptoDto -> cryptoDto.getSlope().compareTo(BigDecimal.ZERO) < 0)
                         .sorted(comparing(CryptoDto::getPriceCountToSlope))
                         .limit(4)
                         .collect(Collectors.toList());
    }


    private void cancelRequest(OrderDto orderToCancel) {
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(orderToCancel.getOrder().getSymbol(), orderToCancel.getOrder().getClientOrderId());
        logger.log("New cancelOrderRequest: " + cancelOrderRequest);
        binanceApiRestClient.cancelOrder(cancelOrderRequest);
    }

    private CryptoDto updateCryptoDtoWithSlopeData(CryptoDto cryptoDto) {
        List<BigDecimal> averagePrices = getAveragePrices(cryptoDto);
        double leastSquaresSlope = getSlope(averagePrices);
        if (Double.isNaN(leastSquaresSlope)) {
            leastSquaresSlope = 0.0000000001;
        }
        BigDecimal slope = new BigDecimal(leastSquaresSlope, new MathContext(8));
        BigDecimal priceCount = new BigDecimal(averagePrices.size(), new MathContext(8));
        cryptoDto.setSlope(slope);
        cryptoDto.setPriceCount(priceCount);
        cryptoDto.setPriceCountToSlope(priceCount.divide(slope, 8, CEILING));
        return cryptoDto;
    }

    private List<BigDecimal> getAveragePrices(CryptoDto cryptoDto) {
        Candlestick candlestick = cryptoDto.getThreeMonthsCandleStickData()
                                           .stream()
                                           .max(comparing(candle -> new BigDecimal(candle.getHigh())))
                                           .orElseThrow();
        return cryptoDto.getThreeMonthsCandleStickData()
                        .stream()
                        .filter(candle -> candle.getOpenTime() > candlestick.getOpenTime())
                        .map(this::getAveragePrice)
                        .collect(Collectors.toList());
    }

    private BigDecimal getAveragePrice(Candlestick candle) {
        BigDecimal open = new BigDecimal(candle.getOpen());
        BigDecimal close = new BigDecimal(candle.getClose());
        BigDecimal high = new BigDecimal(candle.getHigh());
        BigDecimal low = new BigDecimal(candle.getLow());
        return open.add(close)
                   .add(high)
                   .add(low)
                   .divide(new BigDecimal("4"), 8, CEILING);
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

    private BigDecimal buyBnB() {
        logger.log("***** ***** Buying BNB ***** *****");
        BigDecimal myBnbBalance = getMyBalance("BNB");
        if (myBnbBalance.compareTo(new BigDecimal("2")) < 0) {
            BigDecimal currentBnbBtcPrice = getCurrentBnbBtcPrice();
            logger.log("currentBnbBtcPrice: " + currentBnbBtcPrice);
            BigDecimal myBtcBalance = getMyBalance("BTC");
            BigDecimal maxBnbQuantity = myBtcBalance.divide(currentBnbBtcPrice, 8, CEILING);
            logger.log("maxBnbQuantity: " + maxBnbQuantity);
            String quantityToBuy = "1";
            if (maxBnbQuantity.compareTo(ONE) < 0) {
                quantityToBuy = maxBnbQuantity.toPlainString();
            }
            NewOrder buyOrder = new NewOrder("BNBBTC", BUY, MARKET, null, quantityToBuy);
            logger.log("New buyOrder: " + buyOrder);
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

    private void buySmallAmounts(Supplier<List<CryptoDto>> cryptoDtosSupplier) {
        logger.log("***** ***** Buying small amounts ***** *****");
        cryptoDtosSupplier.get()
                          .stream()
                          .map(this::updateCryptoDtoWithLeastMaxAverage)
                          .filter(dto -> dto.getLastThreeMaxAverage().compareTo(dto.getPreviousThreeMaxAverage()) > 0)
                          .map(this::updateCryptoDtoWithPrices)
                          .filter(dto -> dto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                          .map(this::updateCryptoDtoWithSumDiffPerc)
                          .filter(dto -> dto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                          .filter(dto -> dto.getSumDiffsPerc10h().compareTo(new BigDecimal("400")) < 0)
                          .forEach(this::tradeCrypto);
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
        OrderBook depth = getDepth(symbol);
        BigDecimal currentPrice = calculateCurrentPrice(depth);
        cryptoDto.setDepth20(depth);
        cryptoDto.setCurrentPrice(currentPrice);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithLeastMaxAverage(CryptoDto cryptoDto) {
        List<Candlestick> candleStickData = getCandleStickData(cryptoDto, FIFTEEN_MINUTES, 96);
        BigDecimal lastThreeMaxAverage = calculateLastThreeMaxAverage(candleStickData);
        BigDecimal previousThreeMaxAverage = calculatePreviousThreeMaxAverage(candleStickData);
        cryptoDto.setFifteenMinutesCandleStickData(candleStickData);
        cryptoDto.setLastThreeMaxAverage(lastThreeMaxAverage);
        cryptoDto.setPreviousThreeMaxAverage(previousThreeMaxAverage);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithPrices(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal priceToSellPercentage = calculatePriceToSellPercentage(priceToSell, currentPrice);
        cryptoDto.setPriceToSell(priceToSell);
        cryptoDto.setPriceToSellPercentage(priceToSellPercentage);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithSumDiffPerc(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal sumDiffsPerc = calculateSumDiffsPercent(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal sumDiffsPerc10h = calculateSumDiffsPercent10h(fifteenMinutesCandleStickData, currentPrice);
        cryptoDto.setSumDiffsPerc(sumDiffsPerc);
        cryptoDto.setSumDiffsPerc10h(sumDiffsPerc10h);
        return cryptoDto;
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
        logger.log("Trading crypto " + crypto);
        String symbol = crypto.getSymbolInfo().getSymbol();
        BigDecimal myBtcBalance = getMyBalance("BTC");

        // 2. get max possible buy
        OrderBook orderBook = binanceApiRestClient.getOrderBook(symbol, 20);
        OrderBookEntry orderBookEntry = orderBook.getAsks()
                                                 .parallelStream()
                                                 .min(comparing(OrderBookEntry::getPrice))
                                                 .orElseThrow(RuntimeException::new);
        logger.log("OrderBookEntry: " + orderBookEntry);

        // 3. calculate amount to buy
        if (isStillValid(crypto, orderBookEntry) && haveBalanceForBuySmallAmounts(myBtcBalance)) {
            BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
            BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, CEILING);
            BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
            BigDecimal roundedMyQuatity = roundUp(crypto.getSymbolInfo(), min, LOT_SIZE, SymbolFilter::getMinQty);
            BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(crypto.getSymbolInfo(), MIN_NOTIONAL, SymbolFilter::getMinNotional);
            if (roundedMyQuatity.multiply(new BigDecimal(orderBookEntry.getPrice())).compareTo(minNotionalFromMinNotionalFilter) < 0) {
                logger.log("Skip trading due to low trade amount: quantity: " + roundedMyQuatity + ", price: " + orderBookEntry.getPrice());
                return;
            }

            // 4. buy
            NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, roundedMyQuatity.toPlainString());
            logger.log("BuyOrder: " + buyOrder);
            NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(buyOrder);
            // 5. place bid
            if (newOrderResponse.getStatus() == OrderStatus.FILLED) {
                try {
                    placeCompleteSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), roundedMyQuatity);
                } catch (Exception e) {
                    logger.log("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
                    sleep(61000);
                    placeCompleteSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), roundedMyQuatity);
                }
            }
        }
    }

    private BigDecimal getMyBalance(String asset) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
                                      .parallelStream()
                                      .filter(balance -> balance.getAsset().equals(asset))
                                      .map(AssetBalance::getFree)
                                      .map(BigDecimal::new)
                                      .findFirst()
                                      .orElse(BigDecimal.ZERO);
        logger.log("My balance in currency: " + asset + ", is: " + myBalance);
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

    private List<CryptoDto> buyBigAmounts(List<Order> openOrders,
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
         return openOrders.parallelStream()
                         .map(Order::getSymbol)
                         .distinct()
                         .map(symbol -> openOrders.stream()
                                                  .filter(order -> order.getSymbol().equals(symbol))
                                                  .min(getOrderComparator()))
                         .map(Optional::orElseThrow)
                         .map(this::createOrderDto)
                         .filter(orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                         .map(orderDto -> updateOrderDtoWithWaitingTimes(totalAmounts, orderDto))
                         .filter(orderDto -> orderDto.getActualWaitingTime().compareTo(orderDto.getMinWaitingTime()) > 0)
                         .map(this::updateOrderDtoWithPrices)
                         .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                         .peek(orderDto -> logger.log(orderDto.toString()))
                         .map(orderDto -> rebuyOrder(symbolFunction.apply(orderDto),
                                                     orderDto,
                                                     new BigDecimal(countOrdersBySymbol.apply(orderDto)),
                                                     cryptoDtosSupplier,
                                                     totalAmounts,
                                                     exchangeInfo))
                         .filter(list -> !list.isEmpty())
                         .findFirst()
                         .orElseGet(Collections::emptyList);
    }

    private OrderDto createOrderDto(Order order) {
        BigDecimal orderPrice = calculateOrderPrice(order);
        BigDecimal orderBtcAmount = calculateOrderBtcAmount(order, orderPrice);
        OrderDto orderDto = new OrderDto(order);
        orderDto.setOrderPrice(orderPrice);
        orderDto.setOrderBtcAmount(orderBtcAmount);
        return orderDto;
    }

    private OrderDto updateOrderDtoWithWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderDto orderDto) {
        BigDecimal minWaitingTime = calculateMinWaitingTime(totalAmounts.get(orderDto.getOrder().getSymbol()), orderDto.getOrderBtcAmount());
        BigDecimal actualWaitingTime = calculateActualWaitingTime(orderDto.getOrder());
        orderDto.setMinWaitingTime(minWaitingTime);
        orderDto.setActualWaitingTime(actualWaitingTime);
        return orderDto;
    }

    private OrderDto updateOrderDtoWithPrices(OrderDto orderDto) {
        BigDecimal orderPrice = orderDto.getOrderPrice();
        BigDecimal currentPrice = OrderUtils.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol()));
        BigDecimal priceToSellWithoutProfit = calculatePriceToSellWithoutProfit(orderPrice, currentPrice);
        BigDecimal priceToSell = calculatePriceToSell(orderPrice, priceToSellWithoutProfit, orderDto.getOrderBtcAmount());
        BigDecimal priceToSellPercentage = OrderUtils.calculatePriceToSellPercentage(priceToSell, orderPrice);
        orderDto.setCurrentPrice(currentPrice);
        orderDto.setPriceToSellWithoutProfit(priceToSellWithoutProfit);
        orderDto.setPriceToSell(priceToSell);
        orderDto.setPriceToSellPercentage(priceToSellPercentage);
        return orderDto;
    }

    private List<CryptoDto> rebuyOrder(SymbolInfo symbolInfo,
                                       OrderDto orderDto,
                                       BigDecimal currentNumberOfOpenOrdersBySymbol,
                                       Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                       Map<String, BigDecimal> totalAmounts,
                                       ExchangeInfo exchangeInfo) {
        logger.log("Rebuying: symbol=" + symbolInfo.getSymbol());
        logger.log("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);
        if ((orderDto.getOrderBtcAmount().compareTo(new BigDecimal("0.02")) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
            return diversify(orderDto, cryptoDtosSupplier, totalAmounts, exchangeInfo);
        } else {
            rebuySingleOrder(symbolInfo, orderDto);
            return emptyList();
        }
    }

    private void rebuySingleOrder(SymbolInfo symbolInfo, OrderDto orderDto) {
        logger.log("OrderDto: " + orderDto);
        BigDecimal mybtcBalance = getMyBalance("BTC");
        if (mybtcBalance.compareTo(orderDto.getOrderBtcAmount()) < 0) {
            logger.log("BTC balance too low, skip rebuy of crypto.");
            return;
        }
        // 1. cancel existing order
        cancelRequest(orderDto);
        // 2. buy
        BigDecimal orderBtcAmount = orderDto.getOrderBtcAmount();
        BigDecimal orderPrice = orderDto.getOrderPrice();
        buy(symbolInfo, orderBtcAmount, orderPrice);

        // 3. create new order
        BigDecimal quantityToSell = getQuantityFromOrder(orderDto);
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        placeCompleteSellOrder(symbolInfo, orderDto.getPriceToSell(), completeQuantityToSell);
    }

    private void placeSellOrders(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal completeQuantityToSell) {
        BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
        BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
        BigDecimal roundedPriceToSell = roundUp(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        roundedPriceToSell = roundedPriceToSell.setScale(8, DOWN);
        BigDecimal minQuantity = getMinQuantity(minValueFromLotSizeFilter, minValueFromLotSizeFilter, minValueFromMinNotionalFilter, roundedPriceToSell);
        placeSellOrderWithFibonacci(completeQuantityToSell, minQuantity, 1, symbolInfo, roundedPriceToSell);
    }

    private BigDecimal buy(SymbolInfo symbolInfo, BigDecimal orderBtcAmount, BigDecimal orderPrice) {
        BigDecimal myQuantity = orderBtcAmount.divide(orderPrice, 8, CEILING);
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
        BigDecimal roundedQuantity = roundUp(symbolInfo, myQuantityToBuy, LOT_SIZE, SymbolFilter::getMinQty);
        NewOrder buyOrder = new NewOrder(symbolInfo.getSymbol(), BUY, MARKET, null, roundedQuantity.toPlainString());
        logger.log("New buyOrder: " + buyOrder);
        binanceApiRestClient.newOrder(buyOrder);
        return roundedQuantity;
    }

    private BigDecimal getMinQuantity(BigDecimal accumulatedMinValue,
                                      BigDecimal minValueFromLotSizeFilter,
                                      BigDecimal minValueFromMinNotionalFilter,
                                      BigDecimal priceToSellFinal) {
        if (accumulatedMinValue.compareTo(minValueFromLotSizeFilter) > 0
            && accumulatedMinValue.multiply(priceToSellFinal).compareTo(minValueFromMinNotionalFilter) > 0) {
            return accumulatedMinValue;
        }
        return getMinQuantity(accumulatedMinValue.add(minValueFromLotSizeFilter), minValueFromLotSizeFilter, minValueFromMinNotionalFilter, priceToSellFinal);
    }

    private void placeSellOrderWithFibonacci(BigDecimal completeQuantityToSell,
                                             BigDecimal minValueFromFilter,
                                             int fibonacciIndex,
                                             SymbolInfo symbolInfo,
                                             BigDecimal priceToSell) {
        logger.log("Complete quantity in fibonacci: " + completeQuantityToSell);
        logger.log("Fibonacci number: " + FIBONACCI_SEQUENCE[fibonacciIndex]);
        BigDecimal quantityToSell = minValueFromFilter.multiply(FIBONACCI_SEQUENCE[fibonacciIndex]);
        logger.log("quantityToSell: " + quantityToSell);
        if (quantityToSell.compareTo(completeQuantityToSell) < 0 && completeQuantityToSell.subtract(quantityToSell).compareTo(minValueFromFilter) > 0) {
            placePartialSellOrder(symbolInfo, priceToSell, quantityToSell);
            placeSellOrderWithFibonacci(completeQuantityToSell.subtract(quantityToSell), minValueFromFilter, fibonacciIndex + 1, symbolInfo, priceToSell);
        } else {
            placeCompleteSellOrder(symbolInfo, priceToSell, completeQuantityToSell);
        }
    }

    private void placeCompleteSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        logger.log("Place complete sell order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal balance = waitUntilHaveBalance(asset, quantity);
        placeSellOrder(symbolInfo, priceToSell, balance);
    }

    private void placePartialSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        logger.log("Place partial sell order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        waitUntilHaveBalance(asset, quantity);
        placeSellOrder(symbolInfo, priceToSell, quantity);
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        BigDecimal roundedBidQuantity = roundDown(symbolInfo, quantity, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal roundedPriceToSell = roundUp(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, roundedBidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        logger.log("My new sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }

    private String getAssetFromSymbolInfo(SymbolInfo symbolInfo) {
        return symbolInfo.getSymbol().substring(0, symbolInfo.getSymbol().length() - 3);
    }

    private BigDecimal waitUntilHaveBalance(String asset, BigDecimal quantity) {
        BigDecimal myBalance = getMyBalance(asset);
        if (myBalance.compareTo(quantity) >= 0) {
            return myBalance;
        } else {
            sleep(500);
            return waitUntilHaveBalance(asset, quantity);
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.log("Error during sleeping");
        }
    }

    private BigDecimal roundUp(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder).add(valueFromFilter);
    }

    private BigDecimal roundDown(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
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
