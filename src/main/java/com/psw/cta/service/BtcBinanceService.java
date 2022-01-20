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
import static com.psw.cta.service.Fibonacci.FIBONACCI_SEQUENCE;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.CEILING;
import static java.util.Collections.emptyList;
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
import com.psw.cta.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.dto.CryptoUtil;
import com.psw.cta.service.dto.OrderDto;
import com.psw.cta.service.dto.OrderDtoUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class BtcBinanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtcBinanceService.class);

    private final BinanceApiRestClient binanceApiRestClient;
    private final OrderDtoUtil orderDtoUtil;
    private final CryptoUtil cryptoUtil;
    private final LeastSquares leastSquares;
    private final Utils utils;

    public BtcBinanceService(BinanceApiRestClient binanceApiRestClient,
                             OrderDtoUtil orderDtoUtil,
                             CryptoUtil cryptoUtil,
                             LeastSquares leastSquares,
                             Utils utils) {
        this.binanceApiRestClient = binanceApiRestClient;
        this.orderDtoUtil = orderDtoUtil;
        this.cryptoUtil = cryptoUtil;
        this.leastSquares = leastSquares;
        this.utils = utils;
    }

    @Time
    @Scheduled(fixedDelay = 1000 * 60 * 30, initialDelay = 0)
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
        Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
        LOGGER.info("totalAmounts: " + totalAmounts);

        ExchangeInfo exchangeInfo = binanceApiRestClient.getExchangeInfo();
        List<CryptoDto> cryptoDtos = buyBigAmounts(openOrders, myBtcBalance, () -> getCryptoDtos(exchangeInfo), totalAmounts, exchangeInfo);
        int uniqueOpenOrdersSize = openOrders.parallelStream()
                                             .collect(toMap(Order::getSymbolWithPrice, order -> order, (order1, order2) -> order1))
                                             .values()
                                             .size();
        LOGGER.info("Unique open orders: " + uniqueOpenOrdersSize);
        binanceApiRestClient.getExchangeInfo();
        if (haveBalanceForBuySmallAmounts(getMyBalance("BTC")) && uniqueOpenOrdersSize <= minOpenOrders) {
            sleep(1000 * 60 * 2);
            buySmallAmounts(() -> getCryptoDtos(cryptoDtos, exchangeInfo));
        }
        binanceApiRestClient.getExchangeInfo();
    }

    private List<CryptoDto> getCryptoDtos(List<CryptoDto> cryptoDtos, ExchangeInfo exchangeInfo) {
        if (!cryptoDtos.isEmpty()) {
            return cryptoDtos;
        } else {
            return getCryptoDtos(exchangeInfo);
        }
    }

    private List<CryptoDto> getCryptoDtos(ExchangeInfo exchangeInfo) {
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
        LOGGER.info("Cryptos count: " + cryptoDtos.size());
        return cryptoDtos;
    }

    private List<CryptoDto> diversify(OrderDto orderToCancel,
                                      Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                      Map<String, BigDecimal> totalAmounts,
                                      ExchangeInfo exchangeInfo) {
        LOGGER.info("******************************");
        LOGGER.info("Diversifying amounts");

        // 1. cancel existing order
        LOGGER.info("orderToCancel: " + orderToCancel);
        cancelRequest(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = getQuantityFromOrder(orderToCancel);
        LOGGER.info("currentQuantity: " + currentQuantity);
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        sellMarketOrder(symbolInfoOfSellOrder, currentQuantity);

        List<CryptoDto> cryptoDtos = cryptoDtosSupplier.get();
        List<CryptoDto> cryptosToBuy = getCryptoToBuy(cryptoDtos, totalAmounts);
        cryptosToBuy.forEach(cryptoToBuy -> {
            // 3. buy
            LOGGER.info("cryptoToBuy: " + cryptoToBuy);
            SymbolInfo symbolInfo = cryptoToBuy.getSymbolInfo();
            LOGGER.info("symbolInfo: " + symbolInfo);
            BigDecimal cryptoToBuyCurrentPrice = cryptoToBuy.getCurrentPrice();
            LOGGER.info("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);

            BigDecimal currentBtcAmount = currentQuantity.multiply(orderToCancel.getCurrentPrice())
                                                         .divide(new BigDecimal(cryptosToBuy.size()), 8, CEILING)
                                                         .multiply(new BigDecimal("2"));
            LOGGER.info("currentBtcAmount: " + currentBtcAmount);
            BigDecimal boughtQuantity = buy(symbolInfo, currentBtcAmount, cryptoToBuyCurrentPrice);
            LOGGER.info("boughtQuantity: " + boughtQuantity);

            // 4. place sell order
            BigDecimal finalPriceWithProfit = getFinalPriceWithProfit(orderToCancel, boughtQuantity);
            LOGGER.info("finalPriceWithProfit: " + finalPriceWithProfit);
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
        LOGGER.info("Sell order: " + symbolInfo.getSymbol() + ", quantity=" + quantity);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal myBalance = waitUntilHaveBalance(asset, quantity);
        BigDecimal roundedBidQuantity = roundDown(symbolInfo, myBalance, LOT_SIZE, SymbolFilter::getMinQty);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, MARKET, null, roundedBidQuantity.toPlainString());
        LOGGER.info("My new sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }

    private BigDecimal getFinalPriceWithProfit(OrderDto orderToCancel, BigDecimal boughtQuantity) {
        BigDecimal totalBtcAmount = orderToCancel.getOrderBtcAmount();
        LOGGER.info("totalBtcAmount: " + totalBtcAmount);
        BigDecimal totalBtcAmountWithProfit = totalBtcAmount.multiply(new BigDecimal("1.05"));
        LOGGER.info("totalBtcAmountWithProfit: " + totalBtcAmountWithProfit);
        return totalBtcAmountWithProfit.divide(boughtQuantity, 8, CEILING);
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
        LOGGER.info("New cancelOrderRequest" + cancelOrderRequest);
        binanceApiRestClient.cancelOrder(cancelOrderRequest);
    }

    private CryptoDto updateCryptoDtoWithSlopeData(CryptoDto cryptoDto) {
        List<BigDecimal> averagePrices = getAveragePrices(cryptoDto);
        double leastSquaresSlope = leastSquares.getSlope(averagePrices);
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

    private void buySmallAmounts(Supplier<List<CryptoDto>> cryptoDtosSupplier) {
        LOGGER.info("******************************");
        LOGGER.info("Buying small amounts");
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
        TickerStatistics ticker24hr = cryptoUtil.calculateTicker24hr(tickers, cryptoDto.getSymbolInfo().getSymbol());
        BigDecimal volume = cryptoUtil.calculateVolume(ticker24hr);
        cryptoDto.setTicker24hr(ticker24hr);
        cryptoDto.setVolume(volume);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithCurrentPrice(CryptoDto cryptoDto) {
        String symbol = cryptoDto.getSymbolInfo().getSymbol();
        OrderBook depth = getDepth(symbol);
        BigDecimal currentPrice = cryptoUtil.calculateCurrentPrice(depth);
        cryptoDto.setDepth20(depth);
        cryptoDto.setCurrentPrice(currentPrice);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithLeastMaxAverage(CryptoDto cryptoDto) {
        List<Candlestick> candleStickData = getCandleStickData(cryptoDto, FIFTEEN_MINUTES, 96);
        BigDecimal lastThreeMaxAverage = cryptoUtil.calculateLastThreeMaxAverage(candleStickData);
        BigDecimal previousThreeMaxAverage = cryptoUtil.calculatePreviousThreeMaxAverage(candleStickData);
        cryptoDto.setFifteenMinutesCandleStickData(candleStickData);
        cryptoDto.setLastThreeMaxAverage(lastThreeMaxAverage);
        cryptoDto.setPreviousThreeMaxAverage(previousThreeMaxAverage);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithPrices(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal priceToSell = cryptoUtil.calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal priceToSellPercentage = cryptoUtil.calculatePriceToSellPercentage(priceToSell, currentPrice);
        cryptoDto.setPriceToSell(priceToSell);
        cryptoDto.setPriceToSellPercentage(priceToSellPercentage);
        return cryptoDto;
    }

    private CryptoDto updateCryptoDtoWithSumDiffPerc(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal sumDiffsPerc = cryptoUtil.calculateSumDiffsPercent(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal sumDiffsPerc10h = cryptoUtil.calculateSumDiffsPercent10h(fifteenMinutesCandleStickData, currentPrice);
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
            BigDecimal roundedMyQuatity = roundUp(crypto.getSymbolInfo(), min, LOT_SIZE, SymbolFilter::getMinQty);
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

    private BigDecimal getMyBalance(String asset) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
                                      .parallelStream()
                                      .filter(balance -> balance.getAsset().equals(asset))
                                      .map(AssetBalance::getFree)
                                      .map(BigDecimal::new)
                                      .findFirst()
                                      .orElse(BigDecimal.ZERO);
        LOGGER.info("My balance in currency: " + asset + ", is: " + myBalance);
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
        LOGGER.info("******************************");
        LOGGER.info("Buying big amounts");
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
                                                  .min(utils.getOrderComparator()))
                         .map(Optional::orElseThrow)
                         .map(this::createOrderDto)
                         .filter(orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0)
                         .map(orderDto -> updateOrderDtoWithWaitingTimes(totalAmounts, orderDto))
                         .filter(orderDto -> orderDto.getActualWaitingTime().compareTo(orderDto.getMinWaitingTime()) > 0)
                         .map(this::updateOrderDtoWithPrices)
                         .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                         .peek(orderDto -> LOGGER.info(orderDto.toString()))
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
        BigDecimal orderPrice = orderDtoUtil.calculateOrderPrice(order);
        BigDecimal orderBtcAmount = orderDtoUtil.calculateOrderBtcAmount(order, orderPrice);
        OrderDto orderDto = new OrderDto(order);
        orderDto.setOrderPrice(orderPrice);
        orderDto.setOrderBtcAmount(orderBtcAmount);
        return orderDto;
    }

    private OrderDto updateOrderDtoWithWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderDto orderDto) {
        BigDecimal minWaitingTime = orderDtoUtil.calculateMinWaitingTime(totalAmounts.get(orderDto.getOrder().getSymbol()), orderDto.getOrderBtcAmount());
        BigDecimal actualWaitingTime = orderDtoUtil.calculateActualWaitingTime(orderDto.getOrder());
        orderDto.setMinWaitingTime(minWaitingTime);
        orderDto.setActualWaitingTime(actualWaitingTime);
        return orderDto;
    }

    private OrderDto updateOrderDtoWithPrices(OrderDto orderDto) {
        BigDecimal orderPrice = orderDto.getOrderPrice();
        BigDecimal currentPrice = orderDtoUtil.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol()));
        BigDecimal priceToSellWithoutProfit = orderDtoUtil.calculatePriceToSellWithoutProfit(orderPrice, currentPrice);
        BigDecimal priceToSell = orderDtoUtil.calculatePriceToSell(orderPrice, priceToSellWithoutProfit);
        BigDecimal priceToSellPercentage = orderDtoUtil.calculatePriceToSellPercentage(priceToSell, orderPrice);
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
        LOGGER.info("Rebuying: symbol=" + symbolInfo.getSymbol());
        LOGGER.info("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        // 1. cancel existing order
        cancelRequest(orderDto);
        // 2. buy
        BigDecimal orderBtcAmount = orderDto.getOrderBtcAmount();
        BigDecimal orderPrice = orderDto.getOrderPrice();
        buy(symbolInfo, orderBtcAmount, orderPrice);

        // 3. create new order
        BigDecimal quantityToSell = getQuantityFromOrder(orderDto);
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);

        if ((orderDto.getOrderBtcAmount().compareTo(new BigDecimal("0.01")) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
            return diversify(orderDto, cryptoDtosSupplier, totalAmounts, exchangeInfo);
        } else {
            placeSellOrder(symbolInfo, orderDto.getPriceToSell(), completeQuantityToSell);
            return emptyList();
        }
    }

    private void placeSellOrders(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal completeQuantityToSell) {
        BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        LOGGER.info("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
        BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        LOGGER.info("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
        BigDecimal roundedPriceToSell = roundUp(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        BigDecimal minQuantity = getMinQuantity(minValueFromLotSizeFilter, minValueFromLotSizeFilter, minValueFromMinNotionalFilter, roundedPriceToSell);
        placeSellOrderWithFibonacci(completeQuantityToSell, minQuantity, 1, symbolInfo, roundedPriceToSell);
    }

    private BigDecimal buy(SymbolInfo symbolInfo, BigDecimal orderBtcAmount, BigDecimal orderPrice) {
        BigDecimal myQuantity = orderBtcAmount.divide(orderPrice, 8, CEILING);
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
        BigDecimal roundedQuantity = roundUp(symbolInfo, myQuantityToBuy, LOT_SIZE, SymbolFilter::getMinQty);
        NewOrder buyOrder = new NewOrder(symbolInfo.getSymbol(), BUY, MARKET, null, roundedQuantity.toPlainString());
        LOGGER.info("New buyOrder: " + buyOrder);
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
        LOGGER.info("Complete quantity in fibonacci: " + completeQuantityToSell);
        LOGGER.info("Fibonacci number: " + FIBONACCI_SEQUENCE[fibonacciIndex]);
        BigDecimal quantityToSell = minValueFromFilter.multiply(FIBONACCI_SEQUENCE[fibonacciIndex]);
        LOGGER.info("quantityToSell: " + quantityToSell);
        if (quantityToSell.compareTo(completeQuantityToSell) < 0) {
            placeSellOrder(symbolInfo, priceToSell, quantityToSell);
            placeSellOrderWithFibonacci(completeQuantityToSell.subtract(quantityToSell), minValueFromFilter, fibonacciIndex + 1, symbolInfo, priceToSell);
        } else {
            placeSellOrder(symbolInfo, priceToSell, completeQuantityToSell);
        }
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        LOGGER.info("Place new order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal myBalance = waitUntilHaveBalance(asset, quantity);
        BigDecimal roundedBidQuantity = roundDown(symbolInfo, myBalance, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal roundedPriceToSell = roundUp(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, roundedBidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        LOGGER.info("My new sellOrder: " + sellOrder);
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
            LOGGER.error("Error during sleeping");
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
