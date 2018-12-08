package com.psw.cta.client;

import com.psw.cta.entity.CryptoResult;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CacheService;
import com.psw.cta.service.dto.AverageProfit;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route
@Theme(Lumo.class)
public class MainView extends VerticalLayout {

    public MainView(@Autowired CacheService cacheService) {
        Component mainLabel = getLabel("Crypto trade analyser");
        Component allTablesLayout = getAllTablesLayout(cacheService);
        add(mainLabel, allTablesLayout);
    }

    private Component getAllTablesLayout(CacheService cacheService) {
        List<CryptoResult> actualCryptos = cacheService.getCryptos();
        CompleteStats stats = cacheService.getCompleteStats();
        AverageProfit averageProfit = cacheService.getAverageProfit();
        Component tablesLayout1H = getTablesLayout(actualCryptos,
                                                   CryptoResult::getPriceToSell1h,
                                                   CryptoResult::getPriceToSellPercentage1h,
                                                   stats.getStats1H(),
                                                   CryptoType.TYPE_1H,
                                                   averageProfit.getAverage2H());
        Component tablesLayout2H = getTablesLayout(actualCryptos,
                                                   CryptoResult::getPriceToSell2h,
                                                   CryptoResult::getPriceToSellPercentage2h,
                                                   stats.getStats2H(),
                                                   CryptoType.TYPE_2H,
                                                   averageProfit.getAverage2H());
        Component tablesLayout5H = getTablesLayout(actualCryptos,
                                                   CryptoResult::getPriceToSell5h,
                                                   CryptoResult::getPriceToSellPercentage5h,
                                                   stats.getStats5H(),
                                                   CryptoType.TYPE_5H,
                                                   averageProfit.getAverage5H());
        return new VerticalLayout(tablesLayout1H, tablesLayout2H, tablesLayout5H);
    }

    private Component getTablesLayout(List<CryptoResult> actualCryptos,
                                      Function<CryptoResult, BigDecimal> priceToSellFunction,
                                      Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                      Stats stats,
                                      CryptoType type,
                                      BigDecimal average) {
        Component actualCryptoLayout = getActualCryptoLayout(actualCryptos,
                                                             priceToSellFunction,
                                                             priceToSellPercFunction,
                                                             type);
        Component statisticsLayout = getStatisticsLayout(stats, average);
        return new HorizontalLayout(actualCryptoLayout, statisticsLayout);
    }

    private Component getActualCryptoLayout(List<CryptoResult> actualCryptos,
                                            Function<CryptoResult, BigDecimal> priceToSellFunction,
                                            Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                            CryptoType type) {
        Component actualCryptoLabel = getLabel("Actual cryptos");
        Component cryptoResultGrid = getCryptoJsonGrid(actualCryptos,
                                                       priceToSellFunction,
                                                       priceToSellPercFunction,
                                                       type);
        return new VerticalLayout(actualCryptoLabel, cryptoResultGrid);
    }

    private Component getCryptoJsonGrid(List<CryptoResult> actualCryptos,
                                        Function<CryptoResult, BigDecimal> priceToSellFunction,
                                        Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                        CryptoType type) {
        Grid<CryptoResult> grid = new Grid<>();
        List<CryptoResult> filteredCryptos = actualCryptos.stream()
                .filter(cryptoResult -> type.equals(cryptoResult.getCryptoType()))
                .collect(Collectors.toList());
        grid.setItems(filteredCryptos);
        grid.addColumn(cryptoResult -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(cryptoResult.getCreatedAt()),
                                                               ZoneOffset.systemDefault()))
                .setHeader("Date");
        grid.addColumn(CryptoResult::getSymbol).setHeader("Symbol");
        grid.addColumn(CryptoResult::getCurrentPrice).setHeader("Current price");
        grid.addColumn(priceToSellFunction::apply).setHeader("Price to sell");
        grid.addColumn(priceToSellPercFunction::apply).setHeader("Percent");
        grid.setWidth("1000px");
        return grid;
    }

    private Component getStatisticsLayout(Stats stats, BigDecimal average) {
        Component statisticsLabel = getLabel("Statistics");
        Component statisticsDetailLayout = getStatisticsDetailLayout(stats);
        Component statisticsProfitLayout = getStatisticsProfitLayout(average.toString());
        return new VerticalLayout(statisticsLabel, statisticsDetailLayout, statisticsProfitLayout);
    }

    private Component getLabel(String text) {
        Label messageLabel = new Label();
        messageLabel.setText(text);
        messageLabel.setWidth("500px");
        return messageLabel;
    }

    private Component getStatisticsDetailLayout(Stats stats) {
        Component oneMonthStatsLayout = createStatsLayout("One day", stats.getOneDayStats());
        Component oneWeekStatsLayout = createStatsLayout("One week", stats.getOneWeekStats());
        Component oneDayStatsLayout = createStatsLayout("One month", stats.getOneMonthStats());
        return new HorizontalLayout(oneDayStatsLayout, oneWeekStatsLayout, oneMonthStatsLayout);
    }

    private Component getStatisticsProfitLayout(String average) {
        Component averagePercentLabel = createTextField("Percentual profit", average);
        return new HorizontalLayout(averagePercentLabel);
    }

    private Component createStatsLayout(String labelName, double stats) {
        Component textField = createTextField(labelName, String.valueOf(stats));
        return new VerticalLayout(textField);
    }

    private Component createTextField(String label, String value) {
        TextField textField = new TextField();
        textField.setLabel(String.valueOf(label));
        textField.setValue(value);
        textField.setReadOnly(true);
        return textField;
    }
}