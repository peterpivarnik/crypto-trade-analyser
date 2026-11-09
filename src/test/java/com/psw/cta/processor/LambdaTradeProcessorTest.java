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
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.service.AcquireService;
import com.psw.cta.service.CancelService;
import com.psw.cta.service.CryptoService;
import com.psw.cta.service.ExtractService;
import com.psw.cta.service.ForbiddenPairsService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.SplitService;
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
    private BinanceAdapter binanceAdapter;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private SplitService splitService;
    @Mock
    private AcquireService acquireService;
    @Mock
    private RepeatTradingService repeatTradingService;
    @Mock
    private ExtractService extractService;
    @Mock
    private CancelService cancelService;
    @Mock
    private ForbiddenPairsService forbiddenPairsService;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;

    private LambdaTradeProcessor tradeProcessor;

    @BeforeEach
    void setUp() {
        tradeProcessor = new LambdaTradeProcessor(binanceAdapter,
                                                  cryptoService,
                                                  splitService,
                                                  acquireService,
                                                  repeatTradingService,
                                                  extractService,
                                                  cancelService,
                                                  forbiddenPairsService,
                                                  logger);
        lenient().when(forbiddenPairsService.getAllForbiddenPairs()).thenReturn(List.of(SYMBOL));
        lenient().when(binanceAdapter.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        lenient().when(binanceAdapter.getCandlesticks(any(), any(), any())).thenReturn(List.of(candlestick()));
    }

    @Test
    void shouldSellDelistedOrderWhenThereIsNoCryptoToSplitInto() {
        when(splitService.hasCryptosToBuy(any(), any())).thenReturn(false);

        trade();

        verify(cancelService).cancelAndSell(any(), eq(exchangeInfo));
        verify(splitService, never()).splitCancelledOrder(any(), anyString(), any(), any(), any());
    }

    @Test
    void shouldSplitDelistedOrderWhenThereIsCryptoToSplitInto() {
        when(splitService.hasCryptosToBuy(any(), any())).thenReturn(true);

        trade();

        verify(splitService).splitCancelledOrder(any(), eq(SYMBOL), eq(exchangeInfo), any(), any());
        verify(cancelService, never()).cancelAndSell(any(), any());
    }

    @Test
    void shouldSellDelistedOrderWithHighLossInsteadOfRebuyingIt() {
        when(binanceAdapter.getCurrentPrice(SYMBOL)).thenReturn(new BigDecimal("0.00005"));

        trade();

        verify(cancelService).cancelAndSell(any(), eq(exchangeInfo));
        verify(repeatTradingService, never()).rebuySingleOrder(any(), any(), any());
    }

    @Test
    void shouldBuyCryptosWhenThereAreNoOpenOrders() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(BigDecimal.ONE);
        when(acquireService.haveBalanceForInitialTrading(BigDecimal.ONE)).thenReturn(true);

        tradeProcessor.trade(List.of(),
                             Map.of(),
                             BigDecimal.ONE,
                             BigDecimal.ONE,
                             exchangeInfo,
                             0L,
                             0);

        verify(acquireService).initTrading(any(), any());
        verify(splitService, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldCancelTradeWhenBtcBalanceIsBelowMinimum() {
        tradeOldOrder("0.00011", "0.00019999");

        verify(cancelService).cancelTrade(any(), eq(exchangeInfo));
        verify(extractService, never()).extractOnlyFirstOrder(any(), any());
    }

    @Test
    void shouldExtractOrderWhenBtcBalanceIsAtMinimum() {
        tradeOldOrder("0.00011", "0.0002");

        verify(extractService).extractOnlyFirstOrder(any(), eq(exchangeInfo));
        verify(cancelService, never()).cancelTrade(any(), any());
    }

    @Test
    void shouldSplitOrderBeforeCancellingTradeWhenBtcBalanceIsBelowMinimum() {
        tradeOldOrder("0.00012", "0.0001");

        verify(splitService).splitOrdersWithOrderPricePercGreaterThen10(any(), eq(exchangeInfo), any(), any());
        verify(cancelService, never()).cancelTrade(any(), any());
    }

    @Test
    void shouldSplitOrderWithLowestOrderPriceWhenOrderIsBigEnough() {
        tradeOldOrder("0.00013", "0.001");

        verify(splitService).splitOrderWithLowestOrderPrice(any(), eq(exchangeInfo), any(), any());
    }

    @Test
    void shouldRebuyOrdersBeforeSplittingOrderWithLowestOrderPrice() {
        when(repeatTradingService.shouldBeRebought(any(), any())).thenReturn(true);

        tradeOldOrder("0.00013", "0.001");

        verify(repeatTradingService).rebuyOrders(any(), any(), eq(exchangeInfo));
        verify(splitService, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldRebuyOrderWithHighLossBeforeSplittingOrderWithLowestOrderPrice() {
        tradeOldOrder("0.00016", "10", "0.01", "0.01");

        verify(repeatTradingService).rebuySingleOrder(any(), any(), eq(exchangeInfo));
        verify(splitService, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
    }

    @Test
    void shouldContinueWithExtractWhenNoOrderIsBigEnoughToSplit() {
        tradeOldOrder("0.00013", "5", "0.001", "0.00065");

        verify(splitService, never()).splitOrderWithLowestOrderPrice(any(), any(), any(), any());
        verify(cryptoService, never()).getCryptos(any(), any());
        verify(extractService).extractOnlyFirstOrder(any(), eq(exchangeInfo));
    }

    @Test
    void shouldExtractMoreOrdersWhenThereIsOrderToExtract() {
        tradeOldOrder("0.00012", "0.0035");

        verify(extractService).extractOrders(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotExtractMoreOrdersWhenThereIsNoOrderToExtract() {
        tradeOldOrder("0.00011", "3", "0.0035", "0.01");

        verify(extractService, never()).extractOrders(any(), any(), any());
    }

    @Test
    void shouldNotExtractFirstOrderWhenItIsTooSmall() {
        tradeOldOrder("0.00011", "2", "0.001", "0.01");

        verify(extractService, never()).extractOnlyFirstOrder(any(), any());
    }

    @Test
    void shouldCheckCurrentBtcBalanceWhenRebuyingOrderWithHighLoss() {
        tradeNewOrder("0.0002", "0.02");

        ArgumentCaptor<OrderWrapper> wrapper = ArgumentCaptor.forClass(OrderWrapper.class);
        ArgumentCaptor<BiPredicate<BinanceAdapter, OrderWrapper>> hasBtc = ArgumentCaptor.captor();
        verify(repeatTradingService).rebuySingleOrder(wrapper.capture(), hasBtc.capture(), eq(exchangeInfo));
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.0119"));
        assertThat(hasBtc.getValue().test(binanceAdapter, wrapper.getValue())).isFalse();
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.0121"));
        assertThat(hasBtc.getValue().test(binanceAdapter, wrapper.getValue())).isTrue();
    }

    @Test
    void shouldRebuyOrderWithHighLossWithoutWaiting() {
        tradeNewOrder("0.00016", "0.01");

        verify(repeatTradingService).rebuySingleOrder(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotRebuyOrderWithHighLossWithoutNeededBtcAmount() {
        tradeNewOrder("0.00016", "0.005");

        verify(repeatTradingService, never()).rebuySingleOrder(any(), any(), any());
    }

    @Test
    void shouldNotRebuyOrderWithLossBelowThreshold() {
        tradeNewOrder("0.00014", "0.01");

        verify(repeatTradingService, never()).rebuySingleOrder(any(), any(), any());
    }

    private void tradeNewOrder(String orderPrice, String btcBalance) {
        when(binanceAdapter.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
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
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireService.haveBalanceForInitialTrading(any())).thenReturn(true);
        when(repeatTradingService.shouldBeRebought(any(), any())).thenReturn(true);

        tradeNewOrderWithMinOpenOrders("0.00011", 5);

        verify(acquireService).initTrading(any(), eq(Set.of(ACTIVE_SYMBOL)));
        verify(repeatTradingService).rebuyOrders(any(), any(), eq(exchangeInfo));
    }

    @Test
    void shouldNotContinueWhenNewCryptoIsAcquired() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireService.haveBalanceForInitialTrading(any())).thenReturn(true);
        when(acquireService.initTrading(any(), any())).thenReturn(List.of("AAABTC"));

        tradeNewOrderWithMinOpenOrders("0.00011", 5);

        verify(repeatTradingService, never()).rebuyOrders(any(), any(), any());
    }

    @Test
    void shouldLoadCryptosOnlyOnceWhenSplittingAfterAcquiring() {
        when(binanceAdapter.getMyBalance("BTC")).thenReturn(new BigDecimal("0.01"));
        when(acquireService.haveBalanceForInitialTrading(any())).thenReturn(true);

        tradeNewOrderWithMinOpenOrders("0.00012", 5);

        verify(splitService).splitOrdersWithOrderPricePercGreaterThen10(any(), eq(exchangeInfo), any(), any());
        verify(cryptoService, times(1)).getCryptos(any(), any());
    }

    private void tradeNewOrderWithMinOpenOrders(String orderPrice, int minOpenOrders) {
        when(binanceAdapter.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
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

    @Test
    void shouldNotSlowDownExtractedOrderByRestOfSymbol() {
        when(binanceAdapter.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
        Order extractedOrder = symbolOrder("0.00011", "1");
        Order restOrder = symbolOrder("0.000111", "400");

        tradeProcessor.trade(List.of(extractedOrder, restOrder),
                             Map.of(ACTIVE_SYMBOL, new BigDecimal("0.0445")),
                             new BigDecimal("0.001"),
                             new BigDecimal("2"),
                             exchangeInfo,
                             1L,
                             0);

        ArgumentCaptor<OrderWrapper> wrapper = ArgumentCaptor.forClass(OrderWrapper.class);
        verify(repeatTradingService).shouldBeRebought(wrapper.capture(), any());
        assertThat(wrapper.getValue().getQuantity()).isEqualByComparingTo("1");
        assertThat(wrapper.getValue().getRemainWaitingTime()).isNegative();
    }

    private Order symbolOrder(String price, String quantity) {
        Order order = order();
        order.setSymbol(ACTIVE_SYMBOL);
        order.setPrice(price);
        order.setOrigQty(quantity);
        order.setTime(System.currentTimeMillis() - 30L * 3_600_000L);
        return order;
    }

    private void tradeOldOrder(String orderPrice, String btcBalance) {
        String totalAmount = new BigDecimal(orderPrice).multiply(BigDecimal.TEN).toPlainString();
        tradeOldOrder(orderPrice, "10", btcBalance, totalAmount);
    }

    private void tradeOldOrder(String orderPrice, String quantity, String btcBalance, String totalAmount) {
        when(binanceAdapter.getCurrentPrice(ACTIVE_SYMBOL)).thenReturn(new BigDecimal("0.0001"));
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
