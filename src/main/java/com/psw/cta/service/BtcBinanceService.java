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
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.aspect.Time;
import com.psw.cta.service.dto.CryptoDto;
import com.psw.cta.service.dto.OrderDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        OrderRequest orderRequest = new OrderRequest(null);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        LOGGER.info("Number of open orders: " + openOrders.size());
        buyBigAmounts(openOrders, getMyBalance("BTC"));
        long openSmallTrades = openOrders.stream()
                .filter(order -> new BigDecimal("0.0003").compareTo(new BigDecimal(order.getPrice()).multiply(new BigDecimal(order.getOrigQty()))) > 0)
                .count();
        LOGGER.info("Number of openSmallTrades: " + openSmallTrades);
        BigDecimal myBtcBalance = getMyBalance("BTC");
        if (haveBalanceForBuySmallAmounts(myBtcBalance) && openSmallTrades < 5) {
            buySmallAmounts();
        }
    }

    private void buySmallAmounts() {
        LOGGER.info("ENTERED BUYING SMALL AMOUNTS.");
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
        LOGGER.info("Trading crypto " + crypto.getSymbolInfo().getSymbol());
        String symbol = crypto.getSymbolInfo().getSymbol();
        BigDecimal myBtcBalance = getMyBalance("BTC");

        // 2. get max possible buy
        OrderBook orderBook = binanceApiRestClient.getOrderBook(symbol, 20);
        OrderBookEntry orderBookEntry = orderBook.getAsks()
                .parallelStream()
                .min(Comparator.comparing(OrderBookEntry::getPrice))
                .orElseThrow(RuntimeException::new);
        LOGGER.info("orderBookEntry: " + orderBookEntry);

        // 3. calculate amount to buy
        if (isStillValid(crypto, orderBookEntry) && haveBalanceForBuySmallAmounts(myBtcBalance)) {
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

    private boolean haveBalanceForBuySmallAmounts(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
    }

    private void buyBigAmounts(List<Order> openOrders, BigDecimal myBtcBalance) {
        LOGGER.info("ENTERED BUYING BIG AMOUNTS.");
        openOrders.stream()
                .map(OrderDto::new)
                .peek(orderDto -> orderDto.calculateSumAmounts(openOrders))
                .peek(orderDto -> orderDto.calculateAverageCurrentPrice(openOrders))
                .peek(orderDto -> orderDto.calculateSumCurrentPrice(openOrders))
                .peek(orderDto -> orderDto.calculateMaxOriginalPriceToSell(openOrders))
                .peek(orderDto -> orderDto.calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol())))
                .peek(OrderDto::calculatePriceToSell)
                .filter(orderDto -> orderDto.getTotalBtcAmountToRebuy().compareTo(myBtcBalance) < 0)
                .filter(orderDto -> orderDto.getPriceToSell().compareTo(orderDto.getMaxOriginalPriceToSell()) < 0)
                .peek(OrderDto::calculatePercentualDecreaseBetweenPricesToSell)
                .filter(orderDto -> orderDto.getPercentualDecrease().compareTo(new BigDecimal("0.5")) > 0)
                .peek(OrderDto::calculateCurrentPriceToSellPercentage)
                .peek(OrderDto::calculateIdealRatio)
                .peek(orderDto -> LOGGER.info("Symbol: " + orderDto.getOrder().getSymbol() + ","
                        + " decrease: " + orderDto.getPercentualDecrease() + ", "
                        + "ratio: " + orderDto.getIdealRatio() + ", "
                        + "priceToSell: " + orderDto.getPriceToSell() + ", "
                        + "originalPriceToSell: " + orderDto.getMaxOriginalPriceToSell() + ", "
                        + "priceToSellWithoutProfit: " + orderDto.getPriceToSellWithoutProfit()))
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
        LOGGER.info("Rebuying: symbol=" + symbolInfo.getSymbol() + orderDto.toString());
        BigDecimal totalBtcAmountToRebuy = orderDto.getTotalBtcAmountToRebuy();
        BigDecimal currentPrice = orderDto.getCurrentPrice();
        BigDecimal myQuantity = totalBtcAmountToRebuy.divide(currentPrice, 8, RoundingMode.CEILING);
        BigDecimal minQuantityFromLotSizeFilter = getDataFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        BigDecimal remainder = myQuantity.remainder(minQuantityFromLotSizeFilter);
        BigDecimal filteredMyQuatity = myQuantity.subtract(remainder);
        String symbol = orderDto.getOrder().getSymbol();
        NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, filteredMyQuatity.toPlainString());
        LOGGER.info("BuyOrder: " + buyOrder);
        binanceApiRestClient.newOrder(buyOrder);

        // 2. cancel existing orders
        OrderRequest orderRequest = new OrderRequest(symbol);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        openOrders.forEach(this::cancelOrder);

        // 3. create new order
        sleep();
        placeSellOrder(symbolInfo, minQuantityFromLotSizeFilter, orderDto.getPriceToSell());
    }

    private void cancelOrder(Order order) {
        CancelOrderRequest cancelorderRequest = new CancelOrderRequest(order.getSymbol(), order.getClientOrderId());
        LOGGER.info("cancelorderRequest" + cancelorderRequest);
        binanceApiRestClient.cancelOrder(cancelorderRequest);
    }

    private void placeSellOrder(SymbolInfo symbolInfo, BigDecimal minQuantityFromLotSizeFilter, BigDecimal priceToSell) {
        LOGGER.info("place new order: " + symbolInfo.getSymbol() + "priceToSell=" + priceToSell);
        String currencyShortcut = symbolInfo.getSymbol().replace("BTC", "");
        BigDecimal myBalance = getMyBalance(currencyShortcut);
        BigDecimal bidReminder = myBalance.remainder(minQuantityFromLotSizeFilter);
        BigDecimal bidQuantity = myBalance.subtract(bidReminder);
        BigDecimal tickSizeFromPriceFilter = getDataFromFilter(symbolInfo, PRICE_FILTER, SymbolFilter::getTickSize);
        BigDecimal priceRemainder = priceToSell.remainder(tickSizeFromPriceFilter);
        BigDecimal roundedPriceToSell = priceToSell.subtract(priceRemainder);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, bidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        LOGGER.info("sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);
    }
}
