package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CommonUtils;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class BinanceApiService {

    private final BinanceApiRestClient binanceApiRestClient;
    private final LambdaLogger logger;

    public BinanceApiService(String apiKey, String apiSecret, LambdaLogger logger) {
        this.binanceApiRestClient = new BinanceApiRestClientImpl(apiKey, apiSecret);
        this.logger = logger;
    }

    public List<Order> getOpenOrders() {
        return binanceApiRestClient.getOpenOrders(new OrderRequest(null));
    }

    public ExchangeInfo getExchangeInfo() {
        return binanceApiRestClient.getExchangeInfo();
    }

    public List<TickerStatistics> getAll24hTickers() {
        return binanceApiRestClient.getAll24HrPriceStatistics();
    }

    public OrderBook getOrderBook(String symbol) {
        return binanceApiRestClient.getOrderBook(symbol, 20);
    }

    public List<Candlestick> getCandleStickData(Crypto crypto, CandlestickInterval interval, Integer limit) {
        final String symbol = crypto.getSymbolInfo().getSymbol();
        return binanceApiRestClient.getCandlestickBars(symbol, interval, limit, null, null);
    }

    public OrderBookEntry getMinOrderBookEntry(String symbol) {
        return binanceApiRestClient.getOrderBook(symbol, 20)
                                   .getAsks()
                                   .parallelStream()
                                   .min(comparing(OrderBookEntry::getPrice))
                                   .orElseThrow(RuntimeException::new);
    }

    public BigDecimal getMyTotalBalance() {
        return binanceApiRestClient.getAccount()
                                   .getBalances()
                                   .parallelStream()
                                   .map(this::mapToAssetAndBalance)
                                   .filter(pair -> pair.getLeft().compareTo(ZERO) > 0)
                                   .map(this::mapToBtcBalance)
                                   .reduce(ZERO, BigDecimal::add);
    }

    private Pair<BigDecimal, String> mapToAssetAndBalance(AssetBalance assetBalance) {
        return Pair.of(new BigDecimal(assetBalance.getFree()).add(new BigDecimal(assetBalance.getLocked())), assetBalance.getAsset());
    }

    private BigDecimal mapToBtcBalance(Pair<BigDecimal, String> pair) {
        if (pair.getRight().equals(ASSET_BTC)) {
            return pair.getLeft();
        } else {
            try {
                BigDecimal price = getOrderBook(pair.getRight() + ASSET_BTC)
                    .getBids()
                    .parallelStream()
                    .map(OrderBookEntry::getPrice)
                    .map(BigDecimal::new)
                    .max(BigDecimal::compareTo)
                    .orElse(ZERO);
                return price.multiply(pair.getLeft());
            } catch (BinanceApiException e) {
                return ZERO;
            }
        }
    }

    public BigDecimal getMyBalance(String asset) {
        Account account = binanceApiRestClient.getAccount();
        BigDecimal myBalance = account.getBalances()
                                      .parallelStream()
                                      .filter(balance -> balance.getAsset().equals(asset))
                                      .map(AssetBalance::getFree)
                                      .map(BigDecimal::new)
                                      .findFirst()
                                      .orElse(ZERO);
        logger.log("My balance in currency: " + asset + ", is: " + myBalance);
        return myBalance;
    }

    public void cancelRequest(OrderWrapper orderToCancel) {
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(orderToCancel.getOrder().getSymbol(), orderToCancel.getOrder().getClientOrderId());
        logger.log("New cancelOrderRequest: " + cancelOrderRequest);
        binanceApiRestClient.cancelOrder(cancelOrderRequest);
    }

    public BigDecimal buy(SymbolInfo symbolInfo, BigDecimal btcAmount, BigDecimal price) {
        BigDecimal myQuantity = btcAmount.divide(price, 8, CEILING);
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
        BigDecimal roundedQuantity = roundAmount(symbolInfo, myQuantityToBuy);
        createNewOrder(symbolInfo.getSymbol(), BUY, roundedQuantity);
        return roundedQuantity;
    }

    public void sellAvailableBalance(SymbolInfo symbolInfo, BigDecimal quantity) {
        logger.log("Sell order: " + symbolInfo.getSymbol() + ", quantity=" + quantity);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal myBalance = waitUntilHaveBalance(asset, quantity);
        BigDecimal roundedBidQuantity = roundAmount(symbolInfo, myBalance);
        createNewOrder(symbolInfo.getSymbol(), SELL, roundedBidQuantity);
    }

    public void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
        logger.log("Place sell order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
        String asset = getAssetFromSymbolInfo(symbolInfo);
        BigDecimal balance = waitUntilHaveBalance(asset, quantity);
        BigDecimal roundedBidQuantity = roundAmount(symbolInfo, balance);
        BigDecimal roundedPriceToSell = roundPrice(symbolInfo, priceToSell);
        NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(), SELL, LIMIT, GTC, roundedBidQuantity.toPlainString(), roundedPriceToSell.toPlainString());
        createNewOrder(sellOrder);
    }

    public void createNewOrder(String symbol, OrderSide orderSide, BigDecimal roundedMyQuatity) {
        NewOrder buyOrder = new NewOrder(symbol, orderSide, MARKET, null, roundedMyQuatity.toPlainString());
        createNewOrder(buyOrder);
    }

    private void createNewOrder(NewOrder newOrder) {
        logger.log("My new order: " + newOrder);
        binanceApiRestClient.newOrder(newOrder);
    }

    private String getAssetFromSymbolInfo(SymbolInfo symbolInfo) {
        return symbolInfo.getSymbol().substring(0, symbolInfo.getSymbol().length() - 3);
    }

    private BigDecimal waitUntilHaveBalance(String asset, BigDecimal quantity) {
        BigDecimal myBalance = getMyBalance(asset);
        if (myBalance.compareTo(quantity) >= 0) {
            return myBalance;
        } else {
            CommonUtils.sleep(500, logger);
            return waitUntilHaveBalance(asset, quantity);
        }
    }
}
