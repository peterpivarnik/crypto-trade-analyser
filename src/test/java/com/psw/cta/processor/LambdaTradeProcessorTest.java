package com.psw.cta.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.processor.trade.AcquireProcessor;
import com.psw.cta.processor.trade.CancelProcessor;
import com.psw.cta.processor.trade.CryptoProcessor;
import com.psw.cta.processor.trade.ExtractProcessor;
import com.psw.cta.processor.trade.RepeatTradingProcessor;
import com.psw.cta.processor.trade.SplitProcessor;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LambdaTradeProcessorTest {

    private static final String SYMBOL = "OLDBTC";
    private static final String ACTIVE_SYMBOL = "NEWBTC";

    @Mock
    private BinanceService binanceService;
    @Mock
    private CryptoProcessor cryptoProcessor;
    @Mock
    private SplitProcessor splitProcessor;
    @Mock
    private AcquireProcessor acquireProcessor;
    @Mock
    private RepeatTradingProcessor repeatTradingProcessor;
    @Mock
    private ExtractProcessor extractProcessor;
    @Mock
    private CancelProcessor cancelProcessor;
    @Mock
    private ForbiddenPairsProvider forbiddenPairsProvider;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private LambdaTradeProcessor tradeProcessor;

    @BeforeEach
    void setUp() {
        tradeProcessor = new LambdaTradeProcessor(binanceService,
                                                  cryptoProcessor,
                                                  splitProcessor,
                                                  acquireProcessor,
                                                  repeatTradingProcessor,
                                                  extractProcessor,
                                                  cancelProcessor,
                                                  forbiddenPairsProvider,
                                                  logger);
        lenient().when(forbiddenPairsProvider.getAllForbiddenPairs()).thenReturn(List.of(SYMBOL));
        lenient().when(binanceService.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        lenient().when(binanceService.getCandlesticks(any(), any(), any())).thenReturn(List.of(candlestick()));
    }

    @Test
    void shouldSellDelistedOrderWhenThereIsNoCryptoToSplitInto() {
        when(splitProcessor.hasCryptosToBuy(any(), any())).thenReturn(false);

        trade();

        verify(cancelProcessor).cancelAndSell(any(), eq(exchangeInfo));
        verify(splitProcessor, never()).splitCancelledOrder(any(), anyString(), any(), any(), any());
    }

    @Test
    void shouldSplitDelistedOrderWhenThereIsCryptoToSplitInto() {
        when(splitProcessor.hasCryptosToBuy(any(), any())).thenReturn(true);

        trade();

        verify(splitProcessor).splitCancelledOrder(any(), eq(SYMBOL), eq(exchangeInfo), any(), any());
        verify(cancelProcessor, never()).cancelAndSell(any(), any());
    }

    @Test
    void shouldSellDelistedOrderWithHighLossInsteadOfRebuyingIt() {
        when(binanceService.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.00005"));

        trade();

        verify(cancelProcessor).cancelAndSell(any(), eq(exchangeInfo));
        verify(repeatTradingProcessor, never()).rebuySingleOrder(any(), any(), any());
    }

    @Test
    void shouldBuyCryptosWhenThereAreNoOpenOrders() {
        when(binanceService.getMyBalance("BTC")).thenReturn(BigDecimal.ONE);
        when(acquireProcessor.haveBalanceForInitialTrading(BigDecimal.ONE)).thenReturn(true);

        tradeProcessor.trade(List.of(),
                             Map.of(),
                             BigDecimal.ONE,
                             BigDecimal.ONE,
                             exchangeInfo,
                             0L,
                             0);

        verify(acquireProcessor).initTrading(any(), any());
        verify(splitProcessor, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldCancelTradeWhenBtcBalanceIsBelowMinimum() {
        tradeOldOrder("0.00011", "0.00019999");

        verify(cancelProcessor).cancelTrade(any(), eq(exchangeInfo));
        verify(extractProcessor, never()).extractOnlyFirstOrder(any(), any());
    }

    @Test
    void shouldExtractOrderWhenBtcBalanceIsAtMinimum() {
        tradeOldOrder("0.00011", "0.0002");

        verify(extractProcessor).extractOnlyFirstOrder(any(), eq(exchangeInfo));
        verify(cancelProcessor, never()).cancelTrade(any(), any());
    }

    @Test
    void shouldSplitOrderBeforeCancellingTradeWhenBtcBalanceIsBelowMinimum() {
        tradeOldOrder("0.00012", "0.0001");

        verify(splitProcessor).splitOrdersWithOrderPricePercGreaterThen10(any(), eq(exchangeInfo), any(), any());
        verify(cancelProcessor, never()).cancelTrade(any(), any());
    }

    @Test
    void shouldSplitOrderWithLowestOrderPriceWhenOrderIsBigEnough() {
        tradeOldOrder("0.00013", "0.001");

        verify(splitProcessor).splitOrderWithLowestOrderPrice(any(), eq(exchangeInfo), any(), any());
    }

    @Test
    void shouldRebuyOrdersBeforeSplittingOrderWithLowestOrderPrice() {
        when(repeatTradingProcessor.shouldBeRebought(any(), any())).thenReturn(true);

        tradeOldOrder("0.00013", "0.001");

        verify(repeatTradingProcessor).rebuyOrders(any(), any(), eq(exchangeInfo));
        verify(splitProcessor, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldRebuyOrderWithHighLossBeforeSplittingOrderWithLowestOrderPrice() {
        tradeOldOrder("0.00016", "10", "0.01", "0.01");

        verify(repeatTradingProcessor).rebuySingleOrder(any(), any(), eq(exchangeInfo));
        verify(splitProcessor, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldContinueWithExtractWhenNoOrderIsBigEnoughToSplit() {
        tradeOldOrder("0.00013", "5", "0.001", "0.00065");

        verify(splitProcessor, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
        verify(cryptoProcessor, never()).getCryptos(any(), any());
        verify(extractProcessor).extractOnlyFirstOrder(any(), eq(exchangeInfo));
    }

    @Test
    void shouldExtractMoreOrdersWhenThereIsOrderToExtract() {
        tradeOldOrder("0.00012", "0.0035");

        verify(extractProcessor).extractOrders(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotExtractMoreOrdersWhenThereIsNoOrderToExtract() {
        tradeOldOrder("0.00011", "3", "0.0035", "0.01");

        verify(extractProcessor, never()).extractOrders(any(), any(), any());
    }

    @Test
    void shouldNotExtractFirstOrderWhenItIsTooSmall() {
        tradeOldOrder("0.00011", "2", "0.001", "0.01");

        verify(extractProcessor, never()).extractOnlyFirstOrder(any(), any());
    }

    @Test
    void shouldCheckCurrentBtcBalanceWhenRebuyingOrderWithHighLoss() {
        tradeNewOrder("0.0002", "0.02");

        ArgumentCaptor<OrderWrapper> wrapper = ArgumentCaptor.forClass(OrderWrapper.class);
        ArgumentCaptor<BiPredicate<BinanceService, OrderWrapper>> hasBtc = ArgumentCaptor.captor();
        verify(repeatTradingProcessor).rebuySingleOrder(wrapper.capture(), hasBtc.capture(), eq(exchangeInfo));
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.0119"));
        assertThat(hasBtc.getValue().test(binanceService, wrapper.getValue())).isFalse();
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.0121"));
        assertThat(hasBtc.getValue().test(binanceService, wrapper.getValue())).isTrue();
    }

    @Test
    void shouldRebuyOrderWithHighLossWithoutWaiting() {
        tradeNewOrder("0.00016", "0.01");

        verify(repeatTradingProcessor).rebuySingleOrder(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotRebuyOrderWithHighLossWithoutNeededBtcAmount() {
        tradeNewOrder("0.00016", "0.005");

        verify(repeatTradingProcessor, never()).rebuySingleOrder(any(), any(), any());
    }

    @Test
    void shouldNotRebuyOrderWithLossBelowThreshold() {
        tradeNewOrder("0.00014", "0.01");

        verify(repeatTradingProcessor, never()).rebuySingleOrder(any(), any(), any());
    }

    private void tradeNewOrder(String orderPrice, String btcBalance) {
        when(binanceService.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        Order order = order();
        order.setSymbol(ACTIVE_SYMBOL);
        order.setPrice(orderPrice);
        tradeProcessor.trade(List.of(order),
                             Map.of(ACTIVE_SYMBOL, new BigDecimal("0.01")),
                             new BigDecimal(btcBalance),
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             0);
    }

    @Test
    void shouldContinueWithRebuyWhenNoNewCryptoIsAcquired() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireProcessor.haveBalanceForInitialTrading(any())).thenReturn(true);
        when(repeatTradingProcessor.shouldBeRebought(any(), any())).thenReturn(true);

        tradeNewOrderWithMinOpenOrders("0.00011", 5);

        verify(acquireProcessor).initTrading(any(), eq(Set.of(ACTIVE_SYMBOL)));
        verify(repeatTradingProcessor).rebuyOrders(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotContinueWhenNewCryptoIsAcquired() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireProcessor.haveBalanceForInitialTrading(any())).thenReturn(true);
        when(acquireProcessor.initTrading(any(), any())).thenReturn(List.of("AAABTC"));

        tradeNewOrderWithMinOpenOrders("0.00011", 5);

        verify(repeatTradingProcessor, never()).rebuyOrders(any(), any(), any());
    }

    @Test
    void shouldLoadCryptosOnlyOnceWhenSplittingAfterAcquiring() {
        when(binanceService.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireProcessor.haveBalanceForInitialTrading(any())).thenReturn(true);

        tradeNewOrderWithMinOpenOrders("0.00012", 5);

        verify(splitProcessor).splitOrdersWithOrderPricePercGreaterThen10(any(), eq(exchangeInfo), any(), any());
        verify(cryptoProcessor, times(1)).getCryptos(any(), any());
    }

    private void tradeNewOrderWithMinOpenOrders(String orderPrice, int minOpenOrders) {
        when(binanceService.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        Order order = order();
        order.setSymbol(ACTIVE_SYMBOL);
        order.setPrice(orderPrice);
        tradeProcessor.trade(List.of(order),
                             Map.of(ACTIVE_SYMBOL, new BigDecimal("0.01")),
                             new BigDecimal("0.01"),
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             minOpenOrders);
    }

    private void tradeOldOrder(String orderPrice, String btcBalance) {
        String totalAmount = new BigDecimal(orderPrice).multiply(BigDecimal.TEN).toPlainString();
        tradeOldOrder(orderPrice, "10", btcBalance, totalAmount);
    }

    private void tradeOldOrder(String orderPrice, String quantity, String btcBalance, String totalAmount) {
        when(binanceService.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        Order order = order();
        order.setSymbol(ACTIVE_SYMBOL);
        order.setPrice(orderPrice);
        order.setOrigQty(quantity);
        order.setTime(System.currentTimeMillis() - 1000L * 3_600_000L);
        tradeProcessor.trade(List.of(order),
                             Map.of(ACTIVE_SYMBOL, new BigDecimal(totalAmount)),
                             new BigDecimal(btcBalance),
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             0);
    }

    private void trade() {
        tradeProcessor.trade(List.of(order()),
                             Map.of(SYMBOL, new BigDecimal("0.0011")),
                             BigDecimal.ONE,
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             0);
    }

    private Candlestick candlestick() {
        Candlestick candlestick = new Candlestick();
        candlestick.setHigh("0.00011");
        candlestick.setLow("0.00009");
        return candlestick;
    }

    private Order order() {
        Order order = new Order();
        order.setSymbol(SYMBOL);
        order.setPrice("0.00011");
        order.setOrigQty("10");
        order.setExecutedQty("0");
        order.setTime(System.currentTimeMillis());
        return order;
    }
}
