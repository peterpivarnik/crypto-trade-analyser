package com.psw.cta.processor.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SplitProcessorTest {

    private static final String SYMBOL = "OLDBTC";

    @Mock
    private BinanceService binanceService;
    @Mock
    private LambdaLogger logger;
    @Mock
    private ExchangeInfo exchangeInfo;
    @Mock
    private OrderWrapper orderToSplit;
    @Mock
    private Order order;

    private SplitProcessor splitProcessor;

    @BeforeEach
    void setUp() {
        splitProcessor = new SplitProcessor(binanceService, logger);
        lenient().when(orderToSplit.getOrder()).thenReturn(order);
        lenient().when(order.getSymbol()).thenReturn(SYMBOL);
        lenient().when(order.getOrderId()).thenReturn(1L);
        lenient().when(orderToSplit.getQuantity()).thenReturn(BigDecimal.ONE);
        lenient().when(orderToSplit.getOrderPrice()).thenReturn(new BigDecimal("0.0002"));
        lenient().when(orderToSplit.getCurrentPrice()).thenReturn(new BigDecimal("0.0001"));
        lenient().when(exchangeInfo.getSymbolInfo(SYMBOL)).thenReturn(symbolInfo(SYMBOL));
        lenient().when(binanceService.buyWithBtcs(any(), any())).thenReturn(BigDecimal.ONE);
    }

    @Test
    void shouldKeepOrderAndFailWhenNoCryptoIsAvailable() {
        assertThatThrownBy(() -> split("0.01", List.of()))
            .isInstanceOf(CryptoTraderException.class)
            .hasMessageContaining(SYMBOL);

        verify(binanceService, never()).cancelOrder(any());
        verify(binanceService, never()).sellAvailableBalance(any(), any());
    }

    @Test
    void shouldSpendWholeAmountOnOnlyCrypto() {
        split("0.003", List.of(crypto("AAABTC", false)));

        assertBoughtAmounts(new Bought("AAABTC", "0.003"));
    }

    @Test
    void shouldSpendWholeBigAmountOnOnlyCrypto() {
        split("0.05", List.of(crypto("AAABTC", false)));

        assertBoughtAmounts(new Bought("AAABTC", "0.05"));
    }

    @Test
    void shouldAddFlatCryptosWhenOnlyOneCryptoIsInDowntrend() {
        split("0.003", List.of(crypto("DOWNBTC", "0.005", "0.001"), crypto("FLATBTC", "0.001", "0.00101")));

        List<Bought> bought = boughtAmounts();
        assertThat(bought).extracting(Bought::symbol).containsExactly("FLATBTC", "DOWNBTC");
    }

    @Test
    void shouldGiveBiggestPartToCryptoLosingLeastValueRegardlessOfItsPrice() {
        split("0.01", List.of(crypto("CHEAPBTC", "0.000002", "0.0000014"),
                              crypto("MIDDLEBTC", "0.0001", "0.00009"),
                              crypto("EXPENSIVEBTC", "0.02", "0.0198")));

        List<Bought> bought = boughtAmounts();
        assertThat(bought).extracting(Bought::symbol).containsExactly("EXPENSIVEBTC", "MIDDLEBTC", "CHEAPBTC");
        assertThat(new BigDecimal(bought.get(0).amount())).isEqualByComparingTo("0.0021");
    }

    @Test
    void shouldSpendWholeRemainderOnLastCrypto() {
        split("0.0007", List.of(crypto("AAABTC", false), crypto("BBBBTC", false)));

        assertThat(totalBought()).isEqualByComparingTo("0.0007");
    }

    @Test
    void shouldNotSpendMoreThanRemainingAmount() {
        split("0.0003", List.of(crypto("AAABTC", false), crypto("BBBBTC", false), crypto("CCCBTC", false)));

        assertThat(totalBought()).isEqualByComparingTo("0.0003");
    }

    @Test
    void shouldSpendWholeAmountAcrossMoreCryptos() {
        List<Crypto> cryptos = IntStream.range(0, 3)
                                        .mapToObj(index -> crypto("C" + index + "BTC", false))
                                        .toList();

        split("0.002", cryptos);

        assertThat(totalBought()).isEqualByComparingTo("0.002");
    }

    @Test
    void shouldReportWhetherThereAreCryptosToBuy() {
        List<Crypto> cryptos = List.of(crypto("AAABTC", false));

        assertThat(splitProcessor.hasCryptosToBuy(cryptos, Set.of())).isTrue();
        assertThat(splitProcessor.hasCryptosToBuy(cryptos, Set.of("AAABTC"))).isFalse();
    }

    private void split(String proceeds, List<Crypto> cryptos) {
        lenient().when(binanceService.sellAvailableBalance(any(), any())).thenReturn(new BigDecimal(proceeds));
        splitProcessor.splitCancelledOrder(List.of(orderToSplit), SYMBOL, exchangeInfo, cryptos, Set.of(SYMBOL));
    }

    private void assertBoughtAmounts(Bought expected) {
        List<Bought> bought = boughtAmounts();
        assertThat(bought).hasSize(1);
        assertThat(bought.get(0).symbol()).isEqualTo(expected.symbol());
        assertThat(new BigDecimal(bought.get(0).amount())).isEqualByComparingTo(expected.amount());
    }

    private BigDecimal totalBought() {
        return boughtAmounts().stream()
                              .map(bought -> new BigDecimal(bought.amount()))
                              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Bought> boughtAmounts() {
        ArgumentCaptor<SymbolInfo> symbols = ArgumentCaptor.forClass(SymbolInfo.class);
        ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
        verify(binanceService, atLeast(1)).buyWithBtcs(symbols.capture(), amounts.capture());
        List<Bought> bought = new ArrayList<>();
        for (int i = 0; i < symbols.getAllValues().size(); i++) {
            bought.add(new Bought(symbols.getAllValues().get(i).getSymbol(),
                                  amounts.getAllValues().get(i).toPlainString()));
        }
        return bought;
    }

    private Crypto crypto(String symbol, boolean rising) {
        List<Candlestick> candlesticks = IntStream.range(1, 6)
                                                  .mapToObj(day -> candlestick(rising ? day : 6 - day))
                                                  .toList();
        return new Crypto(symbolInfo(symbol)).setThreeMonthsCandleStickData(candlesticks)
                                             .setCurrentPrice(new BigDecimal("0.001"));
    }

    private Crypto crypto(String symbol, String firstPrice, String lastPrice) {
        BigDecimal first = new BigDecimal(firstPrice);
        BigDecimal step = new BigDecimal(lastPrice).subtract(first).divide(new BigDecimal("4"));
        List<Candlestick> candlesticks = IntStream.range(0, 5)
                                                  .mapToObj(day -> candlestick(first.add(step.multiply(
                                                      new BigDecimal(day)))))
                                                  .toList();
        return new Crypto(symbolInfo(symbol)).setThreeMonthsCandleStickData(candlesticks)
                                             .setCurrentPrice(new BigDecimal("0.001"));
    }

    private Candlestick candlestick(int price) {
        return candlestick(new BigDecimal("0.001").multiply(new BigDecimal(price)));
    }

    private Candlestick candlestick(BigDecimal price) {
        String value = price.toPlainString();
        Candlestick candlestick = new Candlestick();
        candlestick.setOpen(value);
        candlestick.setHigh(value);
        candlestick.setLow(value);
        candlestick.setClose(value);
        return candlestick;
    }

    private SymbolInfo symbolInfo(String symbol) {
        SymbolInfo symbolInfo = new SymbolInfo();
        symbolInfo.setSymbol(symbol);
        return symbolInfo;
    }

    private record Bought(String symbol, String amount) {
    }
}
