package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.MAX_NUM_ORDERS;
import static com.psw.cta.service.TradingService.ASSET_BTC;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSellWithoutProfit;
import static com.psw.cta.utils.OrderUtils.getQuantityFromOrder;
import static java.util.Collections.emptyList;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.CryptoDto;
import com.psw.cta.dto.OrderDto;
import com.psw.cta.utils.OrderUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RepeatTradingService {

    private final DiversifyService diversifyService;
    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public RepeatTradingService(DiversifyService diversifyService, BinanceApiService binanceApiService, LambdaLogger logger) {
        this.diversifyService = diversifyService;
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public OrderDto createOrderDto(Order order) {
        BigDecimal orderPrice = calculateOrderPrice(order);
        BigDecimal orderBtcAmount = calculateOrderBtcAmount(order, orderPrice);
        OrderDto orderDto = new OrderDto(order);
        orderDto.setOrderPrice(orderPrice);
        orderDto.setOrderBtcAmount(orderBtcAmount);
        return orderDto;
    }

    public OrderDto updateOrderDtoWithWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderDto orderDto) {
        BigDecimal minWaitingTime = calculateMinWaitingTime(totalAmounts.get(orderDto.getOrder().getSymbol()), orderDto.getOrderBtcAmount());
        BigDecimal actualWaitingTime = calculateActualWaitingTime(orderDto.getOrder());
        orderDto.setMinWaitingTime(minWaitingTime);
        orderDto.setActualWaitingTime(actualWaitingTime);
        return orderDto;
    }

    public OrderDto updateOrderDtoWithPrices(OrderDto orderDto) {
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

    public synchronized List<CryptoDto> repeatTrade(SymbolInfo symbolInfo,
                                                    OrderDto orderDto,
                                                    BigDecimal currentNumberOfOpenOrdersBySymbol,
                                                    Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                                    Map<String, BigDecimal> totalAmounts,
                                                    ExchangeInfo exchangeInfo) {
        logger.log("Rebuying: symbol=" + symbolInfo.getSymbol());
        logger.log("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);
        if ((orderDto.getOrderBtcAmount().compareTo(new BigDecimal("0.02")) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
            return diversifyService.diversify(orderDto, cryptoDtosSupplier, totalAmounts, exchangeInfo);
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
}
