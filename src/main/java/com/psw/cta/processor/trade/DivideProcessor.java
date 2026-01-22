package com.psw.cta.processor.trade;

import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Processor that handles the division of a crypto order into multiple smaller orders.
 * This processor cancels an existing order, sells the crypto, and then redistributes
 * the BTC amount across multiple new crypto purchases with prepared sell orders.
 */
public class DivideProcessor implements CryptoToBuyProvider {

    private final BinanceService binanceService;
    private final LambdaLogger logger;

    /**
     * Constructs a new DivideProcessor with the required dependencies.
     *
     * @param binanceService the service for interacting with Binance API
     * @param logger         the Lambda logger for logging operations
     */
    public DivideProcessor(BinanceService binanceService, LambdaLogger logger) {
        this.binanceService = binanceService;
        this.logger = logger;
    }

    /**
     * Divides a crypto order into multiple smaller orders across different cryptocurrencies.
     * The BTC amount is split into four parts with ratios 7:5:3:1 (16 total parts).
     *
     * @param existingSymbols the set of symbols with existing orders
     * @param cryptos         the list of available cryptocurrencies
     * @param orderWrappers   the list of order wrappers containing order information
     * @param symbol          the symbol of the order to divide
     * @param exchangeInfo    the exchange information containing symbol details
     * @return a string containing details of all four prepared sell orders
     */
    public String divide(Set<String> existingSymbols,
                         List<Crypto> cryptos,
                         List<OrderWrapper> orderWrappers,
                         String symbol,
                         ExchangeInfo exchangeInfo) {
        List<Crypto> cryptosToBuy = getCryptosToBuy(cryptos, existingSymbols);
        return orderWrappers.stream()
                            .filter(orderWrapper -> orderWrapper.getOrder().getSymbol().equals(symbol))
                            .findFirst()
                            .map(orderWrapper -> divideCrypto(orderWrapper, exchangeInfo, cryptosToBuy))
                            .orElseThrow();
    }

    private String divideCrypto(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo, List<Crypto> cryptosToBuy) {
        // 1. cancel existing order
        binanceService.cancelOrder(orderToCancel);

        // 2. sell cancelled order
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        BigDecimal btcAmount = orderToCancel.getOrderBtcAmount();
        binanceService.sellAvailableBalance(symbolInfoOfSellOrder, orderToCancel.getQuantity());

        BigDecimal sevenParts = btcAmount.divide(new BigDecimal("16"), 8, UP).multiply(new BigDecimal("7"));
        BigDecimal fiveParts = btcAmount.divide(new BigDecimal("16"), 8, UP).multiply(new BigDecimal("5"));
        BigDecimal threeParts = btcAmount.divide(new BigDecimal("16"), 8, UP).multiply(new BigDecimal("3"));
        BigDecimal rest = btcAmount.subtract(sevenParts).subtract(fiveParts).subtract(threeParts);

        String preparedSellOrder1 = buyAndPrepareSell(cryptosToBuy.get(0), sevenParts, orderToCancel);
        String preparedSellOrder2 = buyAndPrepareSell(cryptosToBuy.get(1), fiveParts, orderToCancel);
        String preparedSellOrder3 = buyAndPrepareSell(cryptosToBuy.get(2), threeParts, orderToCancel);
        String preparedSellOrder4 = buyAndPrepareSell(cryptosToBuy.get(3), rest, orderToCancel);
        return preparedSellOrder1 + "\n" + preparedSellOrder2 + "\n" + preparedSellOrder3 + "\n" + preparedSellOrder4;
    }

    private String buyAndPrepareSell(Crypto cryptoToBuy, BigDecimal btcAmountToSpend, OrderWrapper orderToCancel) {
        // 3. buy
        BigDecimal boughtQuantity = binanceService.buyWithBtcs(cryptoToBuy.getSymbolInfo(), btcAmountToSpend);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4. log sell order with price and quantity
        BigDecimal finalPriceWithProfit = cryptoToBuy.getCurrentPrice()
                                                     .multiply(orderToCancel.getOrderPrice())
                                                     .multiply(new BigDecimal("1.01"))
                                                     .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
        String preparedSellOrder = "Symbol: "
                                   + cryptoToBuy.getSymbolInfo().getSymbol()
                                   + ", Price: "
                                   + finalPriceWithProfit
                                   + ", Quantity: "
                                   + boughtQuantity;
        logger.log(preparedSellOrder);
        return preparedSellOrder;
    }
}
