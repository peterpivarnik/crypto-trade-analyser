package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.Constants.TIME_CONSTANT;
import static com.psw.cta.utils.Constants.TWO;
import static com.psw.cta.utils.LeastSquares.getRegression;
import static java.lang.Math.sqrt;
import static java.math.BigDecimal.valueOf;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.ofInstant;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolInfo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class OrderUtils {

    private static final BigDecimal HALF_OF_MAX_PROFIT = new BigDecimal("0.15");
    private static final BigDecimal HALF_OF_MIN_PROFIT = new BigDecimal("0.0025");

    public static BigDecimal calculateOrderPrice(Order order) {
        return new BigDecimal(order.getPrice());
    }

    public static BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
        BigDecimal orderAltAmount = getQuantity(order);
        return orderAltAmount.multiply(orderPrice);
    }

    public static BigDecimal calculatePriceToSell(BigDecimal orderPrice,
                                                  BigDecimal currentPrice,
                                                  BigDecimal orderBtcAmount,
                                                  SymbolInfo symbolInfo,
                                                  BigDecimal btcBalanceToTotalBalanceRatio) {
        BigDecimal profitCoefficient = getProfitCoefficient(orderBtcAmount, btcBalanceToTotalBalanceRatio);
        return getNewPriceToSell(orderPrice, currentPrice, symbolInfo, profitCoefficient);
    }

    private static BigDecimal getProfitCoefficient(BigDecimal orderBtcAmount, BigDecimal btcBalanceToTotalBalanceRatio) {
        BigDecimal maxBtcAmountToReduceProfit = btcBalanceToTotalBalanceRatio.divide(new BigDecimal("5"), 8, CEILING);
        SimpleRegression regression =
            getRegression(0.0001, HALF_OF_MAX_PROFIT.doubleValue(), maxBtcAmountToReduceProfit.doubleValue(), HALF_OF_MIN_PROFIT.doubleValue());
        BigDecimal btcAmountProfitPart = calculateProfitPart(orderBtcAmount, valueOf(regression.getSlope()), valueOf(regression.getIntercept()));
        BigDecimal ratioProfitPart = calculateProfitPart(btcBalanceToTotalBalanceRatio, new BigDecimal("-0.59"), new BigDecimal("0.445"));
        BigDecimal profitPercentage = btcAmountProfitPart.add(ratioProfitPart);
        return profitPercentage.max(new BigDecimal("0.005"));
    }

    private static BigDecimal getNewPriceToSell(BigDecimal orderPrice, BigDecimal currentPrice, SymbolInfo symbolInfo, BigDecimal profitCoefficient) {
        BigDecimal priceToSellWithoutProfit = getPriceToSellWithoutProfit(orderPrice, currentPrice);
        BigDecimal profit = priceToSellWithoutProfit.subtract(currentPrice);
        BigDecimal realProfit = profit.multiply(profitCoefficient);
        BigDecimal priceToSell = priceToSellWithoutProfit.add(realProfit);
        BigDecimal roundedPriceToSell = roundPrice(symbolInfo, priceToSell);
        return roundedPriceToSell.stripTrailingZeros();
    }

    private static BigDecimal calculateProfitPart(BigDecimal x, BigDecimal a, BigDecimal b) {
        BigDecimal halfOfProfit = calculateLineEquation(x, a, b);
        return correctForMinAndMaxValues(halfOfProfit);
    }

    private static BigDecimal calculateLineEquation(BigDecimal x, BigDecimal a, BigDecimal b) {
        return (x.multiply(a)).add(b);
    }

    private static BigDecimal correctForMinAndMaxValues(BigDecimal btcAmountPartBase) {
        BigDecimal maxValue = btcAmountPartBase.min(HALF_OF_MAX_PROFIT);
        return maxValue.max(HALF_OF_MIN_PROFIT);
    }

    private static BigDecimal getPriceToSellWithoutProfit(BigDecimal bigPrice, BigDecimal smallPrice) {
        BigDecimal profit = bigPrice.subtract(smallPrice);
        BigDecimal realProfit = profit.divide(TWO, 8, UP);
        return smallPrice.add(realProfit);
    }

    public static BigDecimal calculateMinWaitingTime(BigDecimal totalSymbolAmount, BigDecimal orderBtcAmount) {
        BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
        BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
        BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
        return waitingTime.multiply(TIME_CONSTANT);
    }

    private static BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
        double totalTime = 100 * sqrt(totalAmount.doubleValue());
        return new BigDecimal(totalTime, new MathContext(3));
    }

    public static BigDecimal calculateActualWaitingTime(Order order) {
        LocalDateTime date = ofInstant(ofEpochMilli(order.getTime()), systemDefault());
        LocalDateTime now = now();
        Duration duration = between(date, now);
        double actualWaitingTimeDouble = (double) duration.get(SECONDS) / (double) 3600;
        return new BigDecimal(actualWaitingTimeDouble, new MathContext(5));
    }

    private OrderUtils() {
    }
}
