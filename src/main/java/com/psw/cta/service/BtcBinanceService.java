package com.psw.cta.service;

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
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.dto.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static java.util.Comparator.comparing;

@Service
class BtcBinanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtcBinanceService.class);

    private final BinanceApiRestClient binanceApiRestClient;

    BtcBinanceService() {
        this.binanceApiRestClient = new BinanceApiRestClientImpl("", "");
    }

    @Time
    @Scheduled(cron = "0 */15 * * * ?")
    public void invest() {
        BigDecimal myBtcBalance = getMyBalance("BTC");
        OrderRequest orderRequest = new OrderRequest(null);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        if (myBtcBalance.compareTo(new BigDecimal("0.05")) > 0) {
            buyBigAmounts(openOrders);
        }
        myBtcBalance = getMyBalance("BTC");
        if (haveBalanceForTrade(myBtcBalance) && openOrders.size() < 10) {
            buySmallAmounts();
        }
    }

    private void buySmallAmounts() {
        LOGGER.info("Entered buying small amounts.");
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
                .max(comparing(CryptoDto::getWeight))
                .ifPresent(this::tradeCrypto);
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
        LOGGER.info("Start trading cryptos!");
        String symbol = crypto.getSymbolInfo().getSymbol();
        LOGGER.info("symbol: " + symbol);
        BigDecimal myBtcBalance = getMyBalance("BTC");

        // 2. get max possible buy
        OrderBook orderBook = binanceApiRestClient.getOrderBook(symbol, 20);
        OrderBookEntry orderBookEntry = orderBook.getAsks()
                .parallelStream()
                .min(Comparator.comparing(OrderBookEntry::getPrice))
                .orElseThrow(RuntimeException::new);
        LOGGER.info("orderBookEntry: " + orderBookEntry);

        // 3. calculate amount to buy
        if (isStillValid(crypto, orderBookEntry) && haveBalanceForTrade(myBtcBalance)) {
            BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
            BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, RoundingMode.CEILING);
            BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
            BigDecimal minQuantityFromLotSizeFilter = getDataFromFilter(crypto.getSymbolInfo(), LOT_SIZE, SymbolFilter::getMinQty);
            BigDecimal remainder = min.remainder(minQuantityFromLotSizeFilter);
            BigDecimal filteredMyQuatity = min.subtract(remainder);
            BigDecimal minNotionalFromMinNotionalFilter = getDataFromFilter(crypto.getSymbolInfo(), MIN_NOTIONAL, SymbolFilter::getMinNotional);
            if (filteredMyQuatity.multiply(new BigDecimal(orderBookEntry.getPrice())).compareTo(minNotionalFromMinNotionalFilter) < 0) {
                LOGGER.info("Skip trading due to low trade amount: quantity: " + filteredMyQuatity + ", price: " + orderBookEntry.getPrice());
                return;
            }

            // 4. buy
            NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, filteredMyQuatity.toPlainString());
            LOGGER.info("buyOrder: " + buyOrder);
            NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(buyOrder);
            // 5. place bid
            if (newOrderResponse.getStatus() == OrderStatus.FILLED) {
                sleep();
                placeSellOrder(crypto.getSymbolInfo(), minQuantityFromLotSizeFilter, crypto.getPriceToSell());
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            LOGGER.error("Error during sleeping");
        }
    }

    private BigDecimal getDataFromFilter(SymbolInfo symbolInfo, FilterType lotSize, Function<SymbolFilter, String> getMinQty) {
        return symbolInfo.getFilters()
                .stream()
                .filter(filter -> filter.getFilterType().equals(lotSize))
                .map(getMinQty)
                .map(BigDecimal::new)
                .findAny()
                .orElse(BigDecimal.ONE);
    }

    private BigDecimal getMyBalance(String symbol) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
                .stream()
                .filter(balance -> balance.getAsset().equals(symbol))
                .peek(assetBalance -> LOGGER.info("Current assetBalance: " + assetBalance.toString()))
                .map(AssetBalance::getFree)
                .map(BigDecimal::new)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        LOGGER.info("myBalance in currency: " + symbol + ", is: " + myBalance);
        return myBalance;
    }

    private boolean isStillValid(CryptoDto crypto, OrderBookEntry orderBookEntry) {
        return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
    }

    private boolean haveBalanceForTrade(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0001")) > 0;
    }

    private void buyBigAmounts(List<Order> openOrders) {
        LOGGER.info("Entered buying big amounts.");
        openOrders.stream()
                .map(OrderDto::new)
                .peek(orderDto -> orderDto.calculateSumAmounts(openOrders))
                .peek(orderDto -> orderDto.calculateAverageCurrentPrice(openOrders))
                .peek(orderDto -> orderDto.calculateSumCurrentPrice(openOrders))
                .peek(orderDto -> orderDto.calculateMaxOriginalPriceToSell(openOrders))
                .peek(orderDto -> orderDto.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol())))
                .peek(OrderDto::calculatePriceToSell)
                .peek(orderDto -> orderDto.calculatePercentualDecreaseBetweenPricesToSell(openOrders))
                .filter(orderDto -> orderDto.getPercentualDecrease().compareTo(BigDecimal.ONE) > 0)
                .peek(orderDto -> orderDto.calculateCurrentPriceToSellPercentage(openOrders))
                .peek(OrderDto::calculateIdealRatio)
                .max(comparing(OrderDto::getIdealRatio))
                .ifPresent(this::rebuy);
    }

    private void rebuy(OrderDto orderDto) {
        LOGGER.info(orderDto.print());
        binanceApiRestClient.getExchangeInfo()
                .getSymbols()
                .stream()
                .filter(symbolInfo -> symbolInfo.getSymbol().equals(orderDto.getOrder().getSymbol()))
                .findFirst()
                .ifPresent(symbolInfo -> rebuyOrder(symbolInfo, orderDto));
    }

    private void rebuyOrder(SymbolInfo symbolInfo, OrderDto orderDto) {
        // 1. buy
        BigDecimal myBalanceToRebuy = new BigDecimal("0.05");
        LOGGER.info("My BTC balance: " + myBalanceToRebuy);
        BigDecimal currentPrice = orderDto.getCurrentPrice();
        LOGGER.info("currentPrice: " + currentPrice);
        BigDecimal myQuantity = myBalanceToRebuy.divide(currentPrice, 8, RoundingMode.CEILING);
        LOGGER.info("myQuantity: " + myQuantity);
        BigDecimal minQuantityFromLotSizeFilter = getDataFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        LOGGER.info("minQuantityFromLotSizeFilter: " + minQuantityFromLotSizeFilter);
        BigDecimal remainder = myQuantity.remainder(minQuantityFromLotSizeFilter);
        LOGGER.info("remainder: " + remainder);
        BigDecimal filteredMyQuatity = myQuantity.subtract(remainder);
        LOGGER.info("filteredMyQuatity: " + filteredMyQuatity);
        String symbol = orderDto.getOrder().getSymbol();
        NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, filteredMyQuatity.toPlainString());
        binanceApiRestClient.newOrder(buyOrder);
        LOGGER.info("BuyOrder: " + buyOrder);

        // 2. cancel existing orders
        OrderRequest orderRequest = new OrderRequest(symbol);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        openOrders.forEach(this::cancelOrder);

        // 3. create new order
        placeSellOrder(symbolInfo, minQuantityFromLotSizeFilter, orderDto.getPriceToSell());
    }

    private void cancelOrder(Order order) {
        LOGGER.info("order: " + order);
        CancelOrderRequest cancelorderRequest = new CancelOrderRequest(order.getSymbol(), order.getClientOrderId());
        LOGGER.info("cancelorderRequest" + cancelorderRequest);
        binanceApiRestClient.cancelOrder(cancelorderRequest);
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal minQuantityFromLotSizeFilter, BigDecimal priceToSell) {
        String currencyShortcut = symbolInfo.getSymbol().replace("BTC", "");
        LOGGER.info("currencyShortcut: " + currencyShortcut);
        BigDecimal myBalance = getMyBalance(currencyShortcut);
        LOGGER.info("myBalance: " + myBalance);
        BigDecimal bidReminder = myBalance.remainder(minQuantityFromLotSizeFilter);
        LOGGER.info("bidReminder: " + bidReminder);
        BigDecimal bidQuantity = myBalance.subtract(bidReminder);
        LOGGER.info("bidQuantity: " + bidQuantity);
        BigDecimal tickSizeFromPriceFilter = getDataFromFilter(symbolInfo, PRICE_FILTER, SymbolFilter::getTickSize);
        LOGGER.info("tickSizeFromPriceFilter: " + tickSizeFromPriceFilter);
        LOGGER.info("priceToSell: " + priceToSell);
        BigDecimal priceRemainder = priceToSell.remainder(tickSizeFromPriceFilter);
        LOGGER.info("priceRemainder: " + priceRemainder);
        BigDecimal roundedPriceToSell = priceToSell.subtract(priceRemainder);
        LOGGER.info("roundedPriceToSell: " + roundedPriceToSell);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, bidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        LOGGER.info("sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }
}
