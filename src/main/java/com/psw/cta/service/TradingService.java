package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MAX_NUM_ORDERS;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundDown;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.CryptoUtils.calculateCurrentPrice;
import static com.psw.cta.utils.Fibonacci.FIBONACCI_SEQUENCE;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSellWithoutProfit;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.DOWN;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerStatistics;
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

public class TradingService {

    public static final String ASSET_BNB = "BNB";
    public static final String ASSET_BTC = "BTC";
    public static final String SYMBOL_BNB_BTC = "BNBBTC";
    public static final BigDecimal MIN_PROFIT_PERCENT = new BigDecimal("0.5");

    private final InitialTradingService initialTradingService;
    private final BnbService bnbService;
    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public TradingService(InitialTradingService initialTradingService,
                          BnbService bnbService,
                          BinanceApiService binanceApiService,
                          LambdaLogger logger) {
        this.initialTradingService = initialTradingService;
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
        List<CryptoDto> cryptoDtos = buyBigAmounts(openOrders, myBtcBalance, () -> getCryptoDtos(exchangeInfo), totalAmounts, exchangeInfo);
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

    private List<CryptoDto> diversify(OrderDto orderToCancel,
                                      Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                      Map<String, BigDecimal> totalAmounts,
                                      ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Diversifying amounts ***** *****");

        // 1. cancel existing order
        logger.log("orderToCancel: " + orderToCancel);
        binanceApiService.cancelRequest(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = getQuantityFromOrder(orderToCancel);
        logger.log("currentQuantity: " + currentQuantity);
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        binanceApiService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);

        List<CryptoDto> cryptoDtos = cryptoDtosSupplier.get();
        BigDecimal totalBtcAmountToSpend = currentQuantity.multiply(orderToCancel.getCurrentPrice());
        List<CryptoDto> cryptoToBuy = getCryptoToBuy(cryptoDtos, totalAmounts);
        buyAndSellWithFibonacci(orderToCancel, cryptoToBuy, totalBtcAmountToSpend, 1);
        return cryptoDtos;
    }

    private void buyAndSellWithFibonacci(OrderDto orderToCancel, List<CryptoDto> cryptoToBuy, BigDecimal btcAmountToSpend, int fibonacciIndex) {
        BigDecimal minBtcAmountToTrade = new BigDecimal("0.0001");
        BigDecimal fibonnaciAmountToSpend = minBtcAmountToTrade.multiply(FIBONACCI_SEQUENCE[fibonacciIndex]);
        logger.log("btcAmountToSpend: " + btcAmountToSpend);
        logger.log("fibonnaciAmountToSpend: " + fibonnaciAmountToSpend);
        if (btcAmountToSpend.compareTo(fibonnaciAmountToSpend) > 0) {
            buyAndSell(orderToCancel, cryptoToBuy.get(fibonacciIndex - 1), fibonnaciAmountToSpend);
            buyAndSellWithFibonacci(orderToCancel, cryptoToBuy, btcAmountToSpend.subtract(fibonnaciAmountToSpend), fibonacciIndex + 1);
        } else {
            buyAndSell(orderToCancel, cryptoToBuy.get(fibonacciIndex - 1), btcAmountToSpend);
        }
    }

    private void buyAndSell(OrderDto orderToCancel, CryptoDto cryptoDto, BigDecimal btcAmountToSpend) {
        // 3. buy
        logger.log("cryptoToBuy: " + cryptoDto);
        SymbolInfo symbolInfo = cryptoDto.getSymbolInfo();
        BigDecimal cryptoToBuyCurrentPrice = cryptoDto.getCurrentPrice();
        logger.log("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);
        BigDecimal boughtQuantity = binanceApiService.buy(symbolInfo, btcAmountToSpend, cryptoToBuyCurrentPrice);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4. place sell order
        BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                                 .multiply(new BigDecimal("1.01"))
                                                                 .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);

        BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
        BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
        BigDecimal roundedPriceToSell = roundDown(symbolInfo, finalPriceWithProfit, PRICE_FILTER, SymbolFilter::getTickSize);
        logger.log("roundedPriceToSell: " + roundedPriceToSell);
        roundedPriceToSell = roundedPriceToSell.setScale(8, DOWN);
        logger.log("roundedPriceToSell with scale: " + roundedPriceToSell);
        binanceApiService.placeSellOrder(symbolInfo, finalPriceWithProfit, boughtQuantity);
    }

    private BigDecimal getQuantityFromOrder(OrderDto orderToCancel) {
        BigDecimal originalQuantity = new BigDecimal(orderToCancel.getOrder().getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(orderToCancel.getOrder().getExecutedQty());
        return originalQuantity.subtract(executedQuantity);
    }

    private List<CryptoDto> getCryptoToBuy(List<CryptoDto> cryptoDtos, Map<String, BigDecimal> totalAmounts) {
        List<String> bigOrderKeys = totalAmounts.entrySet()
                                                .parallelStream()
                                                .filter(entry -> entry.getValue().compareTo(new BigDecimal("0.005")) > 0)
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toList());

        return cryptoDtos.stream()
                         .filter(dto -> !bigOrderKeys.contains(dto.getSymbolInfo().getSymbol()))
                         .map(this::updateCryptoDtoWithSlopeData)
                         .filter(cryptoDto -> cryptoDto.getSlope().compareTo(BigDecimal.ZERO) < 0)
                         .sorted(comparing(CryptoDto::getPriceCountToSlope))
                         .collect(Collectors.toList());
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
                        .parallelStream()
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
        return openOrders.stream()
                         .map(Order::getSymbol)
                         .distinct()
                         .map(symbol -> openOrders.parallelStream()
                                                  .filter(order -> order.getSymbol().equals(symbol))
                                                  .min(getOrderComparator()))
                         .map(Optional::orElseThrow)
                         .map(this::createOrderDto)
                         .filter(orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                         .map(orderDto -> updateOrderDtoWithWaitingTimes(totalAmounts, orderDto))
                         .filter(orderDto -> orderDto.getActualWaitingTime().compareTo(orderDto.getMinWaitingTime()) > 0)
                         .map(this::updateOrderDtoWithPrices)
                         .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(MIN_PROFIT_PERCENT) > 0)
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
        BigDecimal currentPrice = OrderUtils.calculateCurrentPrice(binanceApiService.getDepth(orderDto.getOrder().getSymbol()));
        BigDecimal priceToSellWithoutProfit = calculatePriceToSellWithoutProfit(orderPrice, currentPrice);
        BigDecimal priceToSell = calculatePriceToSell(orderPrice, priceToSellWithoutProfit, orderDto.getOrderBtcAmount());
        BigDecimal priceToSellPercentage = OrderUtils.calculatePriceToSellPercentage(currentPrice, priceToSell);
        BigDecimal orderPricePercentage = OrderUtils.calculateOrderPricePercentage(currentPrice, orderPrice);
        orderDto.setCurrentPrice(currentPrice);
        orderDto.setPriceToSellWithoutProfit(priceToSellWithoutProfit);
        orderDto.setPriceToSell(priceToSell);
        orderDto.setPriceToSellPercentage(priceToSellPercentage);
        orderDto.setOrderPricePercentage(orderPricePercentage);
        return orderDto;
    }

    private synchronized List<CryptoDto> rebuyOrder(SymbolInfo symbolInfo,
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
        BigDecimal mybtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
        if (mybtcBalance.compareTo(orderDto.getOrderBtcAmount()) < 0) {
            logger.log("BTC balance too low, skip rebuy of crypto.");
            return;
        }
        // 1. cancel existing order
        binanceApiService.cancelRequest(orderDto);
        // 2. buy
        BigDecimal orderBtcAmount = orderDto.getOrderBtcAmount();
        BigDecimal orderPrice = orderDto.getOrderPrice();
        binanceApiService.buy(symbolInfo, orderBtcAmount, orderPrice);

        // 3. create new order
        BigDecimal quantityToSell = getQuantityFromOrder(orderDto);
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        binanceApiService.placeSellOrder(symbolInfo, orderDto.getPriceToSell(), completeQuantityToSell);
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
