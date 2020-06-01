package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
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

    private BinanceApiRestClient binanceApiRestClient;

    BtcBinanceService() {
        this.binanceApiRestClient = new BinanceApiRestClientImpl("", "");
    }

    @Time
    @Scheduled(cron = "0 */15 * * * ?")
    public void invest() {
        BigDecimal myBtcBalance = getMyBalance("BTC");
        if (myBtcBalance.compareTo(new BigDecimal("0.05")) <= 0) {
            buySmallAmounts(myBtcBalance);
        } else {
            buyBigAmounts();
        }
    }

    private void buyBigAmounts() {
        OrderRequest orderRequest = new OrderRequest(null);
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        openOrders.stream()
            .map(OrderDto::new)
            .peek(orderDto -> orderDto.setSumAmounts(calculateSumAmounts(orderDto, openOrders)))
            .peek(orderDto -> orderDto.setAverageCurrentPriceToSell(calculateAverageCurrentPrice(orderDto, openOrders)))
            .peek(orderDto -> orderDto.setSumCurrentPriceToSell(calculateSumCurrentPrice(orderDto, openOrders)))
            .peek(orderDto -> orderDto.setMaxOriginalPriceToSell(calculateMaxOriginalPriceToSell(orderDto, openOrders)))
            .peek(orderDto -> orderDto.setCurrentPrice(calculateCurrentPrice(getDepth(orderDto.getOrder().getSymbol()))))
            .peek(orderDto -> orderDto.setCurrentPriceDecreasedPercentage(calculate(orderDto)))
            .peek(orderDto -> orderDto.setPriceToSell(calculatePriceToSell(orderDto)))
            .peek(orderDto -> orderDto.setPercentualDecrease(calculatePercentualDecreaseBetweenPricesToSell(orderDto, openOrders)))
            .filter(orderDto -> orderDto.getPercentualDecrease().compareTo(BigDecimal.ONE) > 0)
            .peek(orderDto -> orderDto.setCurrentPriceToSellPercentage(calculateCurrentPriceToSellPercentage(orderDto, openOrders)))
            .peek(orderDto -> orderDto.setIdealRatio(calculateIdealRatio(orderDto)))
            .max(comparing(OrderDto::getIdealRatio))
            .ifPresent(this::rebuy);
    }

    private void rebuy(OrderDto orderDto) {
        System.out.println(orderDto.print());
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
        System.out.println("My BTC balance: " + myBalanceToRebuy);
        BigDecimal currentPrice = orderDto.getCurrentPrice();
        System.out.println("currentPrice: " + currentPrice);
        BigDecimal myQuantity = myBalanceToRebuy.divide(currentPrice, 8, RoundingMode.CEILING);
        System.out.println("myQuantity: " + myQuantity);
        BigDecimal minQuantityFromLotSizeFilter = getDataFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        System.out.println("minQuantityFromLotSizeFilter: " + minQuantityFromLotSizeFilter);
        BigDecimal remainder = myQuantity.remainder(minQuantityFromLotSizeFilter);
        System.out.println("remainder: " + remainder);
        BigDecimal filteredMyQuatity = myQuantity.subtract(remainder);
        System.out.println("filteredMyQuatity: " + filteredMyQuatity);
        NewOrder buyOrder = new NewOrder(orderDto.getOrder().getSymbol(), BUY, MARKET, null, filteredMyQuatity.toString());
        binanceApiRestClient.newOrder(buyOrder);
        System.out.println("BuyOrder: " + buyOrder);

        // 2. cancel existing orders
        OrderRequest orderRequest = new OrderRequest(orderDto.getOrder().getSymbol());
        List<Order> openOrders = binanceApiRestClient.getOpenOrders(orderRequest);
        openOrders.forEach(this::cancelOrder);

        // 3. create new order
        String currencyShortcut = orderDto.getOrder().getSymbol().replace("BTC", "");
        System.out.println("currencyShortcut: " + currencyShortcut);
        BigDecimal myBalance = getMyBalance(currencyShortcut);
        System.out.println("myBalance: " + myBalance);
        BigDecimal bidReminder = myBalance.remainder(minQuantityFromLotSizeFilter);
        System.out.println("bidReminder: " + bidReminder);
        BigDecimal bidQuantity = myBalance.subtract(bidReminder);
        System.out.println("bidQuantity: " + bidQuantity);
        BigDecimal tickSizeFromPriceFilter = getDataFromFilter(symbolInfo, PRICE_FILTER, SymbolFilter::getTickSize);
        System.out.println("tickSizeFromPriceFilter: " + tickSizeFromPriceFilter);
        BigDecimal priceToSell = orderDto.getPriceToSell();
        System.out.println("priceToSell: " + priceToSell);
        BigDecimal priceRemainder = priceToSell.remainder(tickSizeFromPriceFilter);
        System.out.println("priceRemainder: " + priceRemainder);
        BigDecimal roundedPriceToSell = priceToSell.subtract(priceRemainder);
        System.out.println("roundedPriceToSell: " + roundedPriceToSell);

        NewOrder sellOrder = new NewOrder(orderDto.getOrder().getSymbol(), SELL, LIMIT, GTC, bidQuantity.toString(), roundedPriceToSell.toString());
        System.out.println("sellOrder: " + sellOrder);
        binanceApiRestClient.newOrder(sellOrder);

    }

    private void cancelOrder(Order order) {
        System.out.println(order);
        CancelOrderRequest cancelorderRequest = new CancelOrderRequest(order.getSymbol(), order.getClientOrderId());
        System.out.println(cancelorderRequest);
        binanceApiRestClient.cancelOrder(cancelorderRequest);
    }

    private BigDecimal calculateMaxOriginalPriceToSell(OrderDto orderDto, List<Order> openOrders) {
        String symbol = orderDto.getOrder().getSymbol();
        return openOrders.stream()
            .filter(order -> order.getSymbol().equals(symbol))
            .map(Order::getPrice)
            .map(BigDecimal::new)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculateSumCurrentPrice(OrderDto orderDto, List<Order> openOrders) {
        return getSum(orderDto, openOrders, Order::getPrice);
    }

    private BigDecimal calculateSumAmounts(OrderDto orderDto, List<Order> openOrders) {
        return getSum(orderDto, openOrders, Order::getOrigQty);
    }

    private BigDecimal calculateAverageCurrentPrice(OrderDto orderDto, List<Order> openOrders) {
        String symbol = orderDto.getOrder().getSymbol();
        long count = openOrders.stream()
            .filter(order -> order.getSymbol().equals(symbol))
            .count();
        return getSum(orderDto, openOrders, Order::getPrice)
            .divide(new BigDecimal(count), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal getSum(OrderDto orderDto, List<Order> openOrders, Function<Order, String> function) {
        String symbol = orderDto.getOrder().getSymbol();
        return openOrders.stream()
            .filter(order -> order.getSymbol().equals(symbol))
            .map(function)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateIdealRatio(OrderDto orderDto) {
        BigDecimal currentPriceToSellPercentage = orderDto.getCurrentPriceToSellPercentage();
        BigDecimal percentualDecrease = orderDto.getPercentualDecrease();
        if (currentPriceToSellPercentage.compareTo(BigDecimal.ZERO) != 0) {
            return percentualDecrease.divide(currentPriceToSellPercentage, 8, BigDecimal.ROUND_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateCurrentPriceToSellPercentage(OrderDto orderDto, List<Order> openOrders) {
        BigDecimal currentPrice = orderDto.getCurrentPrice();
        BigDecimal currentPriceToSell = calculateCurrentPriceToSellFromOrders(orderDto, openOrders);
        BigDecimal percentage = currentPriceToSell.multiply(new BigDecimal("100")).divide(currentPrice, 8, BigDecimal.ROUND_UP);
        return percentage.subtract(new BigDecimal("100"));
    }

    private BigDecimal calculatePriceToSell(OrderDto orderDto) {
        BigDecimal currentPriceForSell = orderDto.getCurrentPrice().multiply(new BigDecimal("1.01"));
        BigDecimal amountBtcToInvest = new BigDecimal("0.05");
        BigDecimal amountAlterToInvest = amountBtcToInvest.divide(currentPriceForSell, 8, BigDecimal.ROUND_UP);
        BigDecimal totalAlterAmount = amountAlterToInvest.add(orderDto.getSumAmounts());
        BigDecimal maxAlterPrice = orderDto.getMaxOriginalPriceToSell();
        BigDecimal btcAmountFromOrder = orderDto.getSumAmounts().multiply(maxAlterPrice);
        BigDecimal totalBtcAmount = amountBtcToInvest.add(btcAmountFromOrder);
        BigDecimal priceWithoutProfit = totalBtcAmount.divide(totalAlterAmount, 8, BigDecimal.ROUND_UP);
        BigDecimal differenceBetweenMaxAndWithoutProfit = maxAlterPrice.subtract(priceWithoutProfit);
        BigDecimal profit = differenceBetweenMaxAndWithoutProfit.divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP);
        return priceWithoutProfit.add(profit);
    }

    private BigDecimal calculatePercentualDecreaseBetweenPricesToSell(OrderDto orderDto, List<Order> openOrders) {
        BigDecimal newPriceToSell = orderDto.getPriceToSell();
        BigDecimal originalPriceToSell = calculateCurrentPriceToSellFromOrders(orderDto, openOrders);
        return new BigDecimal("100").subtract(newPriceToSell.multiply(new BigDecimal("100")).divide(originalPriceToSell, 8, BigDecimal.ROUND_UP));
    }

    private BigDecimal calculateCurrentPriceToSellFromOrders(OrderDto orderDto, List<Order> openOrders) {
        BigDecimal sumCurrentPrices = orderDto.getSumCurrentPriceToSell();
        long numberOfOrders = openOrders.stream()
            .filter(order -> order.getSymbol().equals(orderDto.getOrder().getSymbol()))
            .count();
        return sumCurrentPrices.divide(new BigDecimal(numberOfOrders), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculate(OrderDto orderDto) {
        BigDecimal averageCurrentPrice = orderDto.getAverageCurrentPriceToSell();
        BigDecimal currentPriceToSellPercentage = orderDto.getCurrentPrice().multiply(new BigDecimal("100")).divide(averageCurrentPrice, 8, RoundingMode.UP);
        return new BigDecimal("100").subtract(currentPriceToSellPercentage);
    }

    private void buySmallAmounts(BigDecimal myBtcBalance) {
        if (!haveBalanceForTrade(myBtcBalance)) {
            LOGGER.info("No balance. Trading is skipped.");
        } else {
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
                .peek(dto -> dto.setTicker24hr(get24hTicker(tickers, dto)))
                .peek(dto -> dto.setVolume(calculateVolume(dto)))
                .filter(dto -> dto.getVolume().compareTo(new BigDecimal("100")) > 0)
                .peek(dto -> dto.setDepth20(getDepth(dto.getSymbolInfo().getSymbol())))
                .peek(dto -> dto.setCurrentPrice(calculateCurrentPrice(dto.getDepth20())))
                .filter(dto -> dto.getCurrentPrice().compareTo(new BigDecimal("0.000001")) > 0)
                .peek(dto -> dto.setFifteenMinutesCandleStickData(getCandleStickData(dto, CandlestickInterval.FIFTEEN_MINUTES, 96)))
                .peek(dto -> dto.setLastThreeMaxAverage(calculateLastThreeMaxAverage(dto)))
                .peek(dto -> dto.setPreviousThreeMaxAverage(calculatePreviousThreeMaxAverage(dto)))
                .filter(dto -> dto.getLastThreeMaxAverage().compareTo(dto.getPreviousThreeMaxAverage()) > 0)
                .peek(dto -> dto.setSumDiffsPerc(calculateSumDiffsPerc(dto, 4)))
                .peek(dto -> dto.setSumDiffsPerc10h(calculateSumDiffsPerc(dto, 40)))
                .peek(dto -> dto.setPriceToSell(calculatePriceToSell(dto)))
                .peek(dto -> dto.setPriceToSellPercentage(calculatePriceToSellPercentage(dto)))
                .filter(dto -> dto.getPriceToSellPercentage().compareTo(new BigDecimal("0.5")) > 0)
                .peek(dto -> dto.setWeight(calculateWeight(dto)))
                .filter(dto -> dto.getSumDiffsPerc().compareTo(new BigDecimal("4")) < 0)
                .filter(dto -> dto.getSumDiffsPerc10h().compareTo(new BigDecimal("400")) < 0)
                .sorted(comparing(CryptoDto::getPriceToSellPercentage).reversed())
                .forEachOrdered(this::tradeCrypto);
        }
    }

    private List<TickerStatistics> getAll24hTickers() {
        return binanceApiRestClient.getAll24HrPriceStatistics();
    }

    private TickerStatistics get24hTicker(List<TickerStatistics> tickers, CryptoDto cryptoDto) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return tickers.parallelStream()
            .filter(ticker -> ticker.getSymbol().equals(symbol))
            .findAny()
            .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    private BigDecimal calculateVolume(CryptoDto cryptoDto) {
        return new BigDecimal(cryptoDto.getTicker24hr().getVolume());
    }

    private OrderBook getDepth(String symbol) {
        return binanceApiRestClient.getOrderBook(symbol, 20);
    }

    private BigDecimal calculateCurrentPrice(OrderBook depth20) {
        return depth20.getAsks()
            .parallelStream()
            .map(OrderBookEntry::getPrice)
            .map(BigDecimal::new)
            .min(Comparator.naturalOrder())
            .orElseThrow(RuntimeException::new);
    }

    private BigDecimal calculateLastThreeMaxAverage(CryptoDto dto) {
        int skipSize = dto.getFifteenMinutesCandleStickData().size() - 3;
        return dto.getFifteenMinutesCandleStickData().stream()
            .skip(skipSize)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal("3"), 8, RoundingMode.UP);
    }

    private BigDecimal calculatePreviousThreeMaxAverage(CryptoDto dto) {
        int skipSize = dto.getFifteenMinutesCandleStickData().size() - 6;
        return dto.getFifteenMinutesCandleStickData().stream()
            .skip(skipSize)
            .limit(3)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal("3"), 8, RoundingMode.UP);
    }

    private List<Candlestick> getCandleStickData(CryptoDto cryptoDto, CandlestickInterval interval, Integer limit) {
        final String symbol = cryptoDto.getSymbolInfo().getSymbol();
        return binanceApiRestClient.getCandlestickBars(symbol, interval, limit, null, null);
    }

    private BigDecimal calculateSumDiffsPerc(CryptoDto cryptoDto, int numberOfDataToKeep) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - numberOfDataToKeep < 0) {
            return BigDecimal.ZERO;
        }
        return calculateSumDiffsPercentage(cryptoDto, size - numberOfDataToKeep);
    }

    private BigDecimal calculateSumDiffsPercentage(CryptoDto cryptoDto, int size) {
        return cryptoDto.getFifteenMinutesCandleStickData().stream()
            .skip(size)
            .map(data -> getPercentualDifference(data, cryptoDto.getCurrentPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPercentualDifference(Candlestick data, BigDecimal currentPrice) {
        BigDecimal absoluteValue = getAverageValue(data);
        BigDecimal relativeValue = absoluteValue.multiply(new BigDecimal("100"))
            .divide(currentPrice, 8, BigDecimal.ROUND_UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    private BigDecimal getAverageValue(Candlestick data) {
        return new BigDecimal(data.getOpen())
            .add(new BigDecimal(data.getClose()))
            .add(new BigDecimal(data.getHigh()))
            .add(new BigDecimal(data.getLow()))
            .divide(new BigDecimal("4"), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal calculatePriceToSell(CryptoDto cryptoDto) {
        int size = cryptoDto.getFifteenMinutesCandleStickData().size();
        if (size - 4 < 0) {
            return BigDecimal.ZERO;
        }
        return cryptoDto.getFifteenMinutesCandleStickData()
            .stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO)
            .subtract(cryptoDto.getCurrentPrice())
            .divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP)
            .add(cryptoDto.getCurrentPrice());
    }

    private BigDecimal calculatePriceToSellPercentage(CryptoDto cryptoDto) {
        BigDecimal priceToSell = cryptoDto.getPriceToSell();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        return priceToSell.multiply(new BigDecimal("100"))
            .divide(currentPrice, 8, BigDecimal.ROUND_UP)
            .subtract(new BigDecimal("100"));
    }

    private BigDecimal calculateWeight(CryptoDto cryptoDto) {
        BigDecimal priceToSell = cryptoDto.getPriceToSell();
        BigDecimal priceToSellPercentage = cryptoDto.getPriceToSellPercentage();
        BigDecimal ratio;
        List<OrderBookEntry> asks = cryptoDto.getDepth20().getAsks();
        final BigDecimal sum = asks.parallelStream()
            .filter(data -> (new BigDecimal(data.getPrice()).compareTo(priceToSell) < 0))
            .map(data -> (new BigDecimal(data.getPrice()).multiply(new BigDecimal(data.getQty()))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0 && priceToSell.compareTo(cryptoDto.getCurrentPrice()) > 0) {
            ratio = new BigDecimal(Double.MAX_VALUE);
        } else if (sum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = BigDecimal.ZERO;
        } else {
            ratio = cryptoDto.getVolume().divide(sum, 8, BigDecimal.ROUND_UP);
        }
        return priceToSellPercentage.multiply(ratio);
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
            BigDecimal myMaxQuantity = myBtcBalance.divide(new BigDecimal(orderBookEntry.getPrice()), 8, RoundingMode.CEILING);
            BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
            BigDecimal minQuantityFromLotSizeFilter = getDataFromFilter(crypto.getSymbolInfo(), LOT_SIZE, SymbolFilter::getMinQty);
            BigDecimal remainder = min.remainder(minQuantityFromLotSizeFilter);
            BigDecimal filteredMyQuatity = min.subtract(remainder);

            // 4. buy
            NewOrder buyOrder = new NewOrder(symbol, BUY, MARKET, null, filteredMyQuatity.toString());
            NewOrderResponse newOrderResponse = binanceApiRestClient.newOrder(buyOrder);
            // 5. place bid
            if (newOrderResponse.getStatus() == OrderStatus.FILLED) {
                BigDecimal executedQuantity = new BigDecimal(newOrderResponse.getExecutedQty());
                BigDecimal bidReminder = executedQuantity.remainder(minQuantityFromLotSizeFilter);
                BigDecimal bidQuantity = executedQuantity.subtract(bidReminder);
                BigDecimal tickSizeFromPriceFilter = getDataFromFilter(crypto.getSymbolInfo(), PRICE_FILTER, SymbolFilter::getTickSize);
                BigDecimal priceToSell = crypto.getPriceToSell();
                BigDecimal priceRemainder = priceToSell.remainder(tickSizeFromPriceFilter);
                BigDecimal roundedPriceToSell = priceToSell.subtract(priceRemainder);
                NewOrder sellOrder = new NewOrder(symbol, SELL, LIMIT, GTC, bidQuantity.toString(), roundedPriceToSell.toString());
                binanceApiRestClient.newOrder(sellOrder);
            }
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
        BigDecimal myBtcBalance = account.getBalances()
            .stream()
            .filter(balance -> balance.getAsset().equals(symbol))
            .map(AssetBalance::getFree)
            .map(BigDecimal::new)
            .findFirst()
            .orElse(BigDecimal.ZERO);
        LOGGER.info("myBtcBalance: " + myBtcBalance);
        return myBtcBalance;
    }

    private boolean isStillValid(CryptoDto crypto, OrderBookEntry orderBookEntry) {
        return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
    }

    private boolean haveBalanceForTrade(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0001")) > 0;
    }
}
