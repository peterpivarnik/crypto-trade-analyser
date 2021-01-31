package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static java.util.Comparator.comparing;

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
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
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
    public void invest() {
        LOGGER.info("******************************************************************************************");
        LOGGER.info("Start of investing.");
        OrderRequest orderRequest = new OrderRequest(null);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        BigDecimal sumFromOrders = openOrders.stream()
            .map(order -> new BigDecimal(order.getPrice())
                .multiply(new BigDecimal(order.getOrigQty())
                              .subtract(new BigDecimal(order.getExecutedQty()))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        LOGGER.info("Number of open orders: " + openOrders.size());
        BigDecimal myBtcBalance = getMyBalance("BTC");
        BigDecimal myTotalPossibleBalance = sumFromOrders.add(myBtcBalance);
        LOGGER.info("My possible balance: " + myTotalPossibleBalance);
        BigDecimal myTotalBalance = getMyTotalBalance();
        LOGGER.info("My actual balance: " + myTotalBalance);
        int minOpenOrders = calculateMinNumberOfOrders(myTotalPossibleBalance, myBtcBalance);
        LOGGER.info("Min open orders: " + minOpenOrders);
        buyBigAmounts(openOrders, myBtcBalance);
        if (haveBalanceForBuySmallAmounts(getMyBalance("BTC")) && openOrders.size() <= minOpenOrders) {
            buySmallAmounts();
        }
    }

    private int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance, BigDecimal myBtcBalance) {
        if (myBtcBalance.compareTo(new BigDecimal("0.1")) < 0) {
            return myTotalPossibleBalance.multiply(new BigDecimal("10")).intValue();
        }
        return myTotalPossibleBalance.multiply(myBtcBalance).multiply(new BigDecimal("100")).intValue();
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
            .peek(dto -> dto.setThreeMonthsCandleStickData(getCandleStickData(dto, CandlestickInterval.DAILY, 90)))
            .filter(dto -> dto.getThreeMonthsCandleStickData().size() >= 90)
            .peek(dto -> dto.calculateTicker24hr(tickers))
            .peek(CryptoDto::calculateVolume)
            .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
            .peek(dto -> dto.setDepth20(getDepth(dto.getSymbolInfo().getSymbol())))
            .peek(CryptoDto::calculateCurrentPrice)
            .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
            .peek(dto -> dto.setFifteenMinutesCandleStickData(getCandleStickData(dto, CandlestickInterval.FIFTEEN_MINUTES, 96)))
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
            .min(Comparator.comparing(OrderBookEntry::getPrice))
            .orElseThrow(RuntimeException::new);
        LOGGER.info("OrderBookEntry: " + orderBookEntry);

        // 3. calculate amount to buy
        if (isStillValid(crypto, orderBookEntry) && haveBalanceForBuySmallAmounts(myBtcBalance)) {
            BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
            BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, RoundingMode.CEILING);
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
                placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), minNotionalFromMinNotionalFilter);
            }
        }
    }

    private BigDecimal getMyBalance(String symbol) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
            .stream()
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
            .stream()
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
                    .stream()
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

    private void buyBigAmounts(List<Order> openOrders, BigDecimal myBtcBalance) {
        LOGGER.info("************************************************************");
        LOGGER.info("Buying big amounts");
        openOrders.stream()
            .map(OrderDto::new)
            .peek(OrderDto::calculateOrderBtcAmount)
            .filter((orderDto -> orderDto.getOrderBtcAmount().compareTo(myBtcBalance) < 0))
            .peek(orderDto -> orderDto.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol())))
            .peek(OrderDto::calculatePriceToSellWithoutProfit)
            .peek(OrderDto::calculatePriceToSell)
            .peek(OrderDto::calculatePriceToSellPercentage)
            .filter(orderDto -> orderDto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
            .peek(orderDto -> LOGGER.info(orderDto.print()))
            .max(comparing(OrderDto::getPriceToSellPercentage))
            .ifPresent(this::rebuy);
    }

    private void rebuy(OrderDto orderDto) {
        binanceApiRestClient.getExchangeInfo()
            .getSymbols()
            .stream()
            .filter(symbolInfo -> symbolInfo.getSymbol().equals(orderDto.getOrder().getSymbol()))
            .findFirst()
            .ifPresent(symbolInfo -> rebuyOrder(symbolInfo, orderDto));
    }

    private void rebuyOrder(SymbolInfo symbolInfo, OrderDto orderDto) {
        // 1. buy
        LOGGER.info("Rebuying: symbol=" + symbolInfo.getSymbol());
        BigDecimal totalBtcAmountToRebuy = orderDto.getOrderBtcAmount();
        BigDecimal myQuantity = totalBtcAmountToRebuy.divide(orderDto.getOrderPrice(), 8, RoundingMode.CEILING);
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
        BigDecimal roundedQuantity = round(symbolInfo, myQuantityToBuy, LOT_SIZE, SymbolFilter::getMinQty);
        NewOrder buyOrder = new NewOrder(orderDto.getOrder().getSymbol(), BUY, MARKET, null, roundedQuantity.toPlainString());
        LOGGER.info("New buyOrder: " + buyOrder);
        binanceApiRestClient.newOrder(buyOrder);

        // 2. cancel existing order
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(orderDto.getOrder().getSymbol(), orderDto.getOrder().getClientOrderId());
        LOGGER.info("New cancelOrderRequest" + cancelOrderRequest);
        binanceApiRestClient.cancelOrder(cancelOrderRequest);

        // 3. create new order
        BigDecimal originalQuantity = new BigDecimal(orderDto.getOrder().getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(orderDto.getOrder().getExecutedQty());
        BigDecimal quantityToRebuy = originalQuantity.subtract(executedQuantity);
        placeSellOrder(symbolInfo, orderDto.getPriceToSell(), quantityToRebuy.multiply(new BigDecimal("2")));
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        LOGGER.info("Place new order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String currencyShortcut = symbolInfo.getSymbol().replace("BTC", "");
        BigDecimal myBalance = waitUntilHaveBalance(currencyShortcut, quantity);
        BigDecimal roundedBidQuantity = round(symbolInfo, myBalance, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal roundedPriceToSell = round(symbolInfo, priceToSell, PRICE_FILTER, SymbolFilter::getTickSize);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, roundedBidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        LOGGER.info("My new sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }

    private BigDecimal waitUntilHaveBalance(String symbol, BigDecimal quantity) {
        BigDecimal myBalance = getMyBalance(symbol);
        if (myBalance.compareTo(quantity) >= 0) {
            return myBalance;
        } else {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOGGER.error("Error during sleeping");
            }
            return waitUntilHaveBalance(symbol, quantity);
        }
    }

    private BigDecimal round(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder);
    }

    private BigDecimal getValueFromFilter(SymbolInfo symbolInfo, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        return symbolInfo.getFilters()
            .stream()
            .filter(filter -> filter.getFilterType().equals(filterType))
            .map(symbolFilterFunction)
            .map(BigDecimal::new)
            .findAny()
            .orElse(BigDecimal.ONE);
    }
}
