package com.psw.cta.client;

import com.psw.cta.entity.CryptoResult;
import com.psw.cta.entity.CryptoType;
import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CryptoService;
import com.psw.cta.service.dto.AverageProfit;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route
public class MainView extends VerticalLayout {


    public MainView(@Autowired CryptoService cryptoService) {
        Label mainLabel = getMainLabel();
        VerticalLayout allTablesLayout = getAllTablesLayout(cryptoService);
        add(mainLabel, allTablesLayout);
    }

    private Label getMainLabel() {
        Label messageLabel = new Label();
        messageLabel.setText("Crypto trade analyser");
        messageLabel.setWidth("500px");
        return messageLabel;
    }

    private VerticalLayout getAllTablesLayout(CryptoService cryptoService) {
        VerticalLayout layout = new VerticalLayout();
        List<CryptoResult> actualCryptos = cryptoService.getActualCryptos();
        CompleteStats stats = cryptoService.getStats();
        AverageProfit averageProfit = cryptoService.getAverageProfit();
        HorizontalLayout tablesLayout2H = getTablesLayout(actualCryptos,
                                                          CryptoResult::getPriceToSell2h,
                                                          CryptoResult::getPriceToSellPercentage2h,
                                                          stats.getStats2H(),
                                                          CryptoType.TYPE_2H,
                                                          averageProfit.getAverage2H());
        HorizontalLayout tablesLayout5H = getTablesLayout(actualCryptos,
                                                          CryptoResult::getPriceToSell5h,
                                                          CryptoResult::getPriceToSellPercentage5h,
                                                          stats.getStats5H(),
                                                          CryptoType.TYPE_5H,
                                                          averageProfit.getAverage5H());
        HorizontalLayout tablesLayout10H = getTablesLayout(actualCryptos,
                                                           CryptoResult::getPriceToSell10h,
                                                           CryptoResult::getPriceToSellPercentage10h,
                                                           stats.getStats10H(),
                                                           CryptoType.TYPE_10H,
                                                           averageProfit.getAverage10H());
        HorizontalLayout tablesLayout24H = getTablesLayout(actualCryptos,
                                                           CryptoResult::getPriceToSell24h,
                                                           CryptoResult::getPriceToSellPercentage24h,
                                                           stats.getStats24H(),
                                                           CryptoType.TYPE_24H,
                                                           averageProfit.getAverage24H());
        layout.add(tablesLayout2H, tablesLayout5H, tablesLayout10H, tablesLayout24H);
        return layout;
    }

    private HorizontalLayout getTablesLayout(List<CryptoResult> actualCryptos,
                                             Function<CryptoResult, BigDecimal> priceToSellFunction,
                                             Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                             Stats stats,
                                             CryptoType type,
                                             BigDecimal average) {
        HorizontalLayout layout = new HorizontalLayout();
        VerticalLayout actualCryptoLayout = getActualCryptoLayout(actualCryptos,
                                                                  priceToSellFunction,
                                                                  priceToSellPercFunction,
                                                                  type);
        VerticalLayout statisticsLayout = getStatisticsLayout(stats, average);
        layout.add(actualCryptoLayout, statisticsLayout);
        return layout;
    }

    private VerticalLayout getActualCryptoLayout(List<CryptoResult> actualCryptos,
                                                 Function<CryptoResult, BigDecimal> priceToSellFunction,
                                                 Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                                 CryptoType type) {
        VerticalLayout layout = new VerticalLayout();
        Label actualCryptoLabel = getActualCryptoLabel();
        Grid<CryptoResult> cryptoResultGrid = getCryptoJsonGrid(actualCryptos,
                                                                priceToSellFunction,
                                                                priceToSellPercFunction,
                                                                type);
        layout.add(actualCryptoLabel, cryptoResultGrid);
        return layout;
    }

    private Label getActualCryptoLabel() {
        Label messageLabel = new Label();
        messageLabel.setText("Actual cryptos");
        messageLabel.setWidth("500px");
        return messageLabel;
    }

    private Grid<CryptoResult> getCryptoJsonGrid(List<CryptoResult> actualCryptos,
                                                 Function<CryptoResult, BigDecimal> priceToSellFunction,
                                                 Function<CryptoResult, BigDecimal> priceToSellPercFunction,
                                                 CryptoType type) {
        Grid<CryptoResult> grid = new Grid<>();
        List<CryptoResult> filteredCryptos = actualCryptos.stream()
                .filter(cryptoResult -> cryptoResult.getCryptoType().equals(type))
                .collect(Collectors.toList());
        grid.setItems(filteredCryptos);
        grid.addColumn(CryptoResult::getCreatedAt).setHeader("Date");
        grid.addColumn(CryptoResult::getSymbol).setHeader("Symbol");
        grid.addColumn(CryptoResult::getCurrentPrice).setHeader("Current price");
        grid.addColumn(priceToSellFunction::apply).setHeader("Price to sell");
        grid.addColumn(priceToSellPercFunction::apply).setHeader("Percent");
        grid.setWidth("1000px");
        return grid;
    }

    private VerticalLayout getStatisticsLayout(Stats stats, BigDecimal average) {
        VerticalLayout layout = new VerticalLayout();
        Label statisticsLabel = getStatisticsLabel();
        Grid<Stats> statisticsGrid = getStatisticsGrid(stats);
        Label averagePercentLabel = getAveragePercentLabel();
        Label averagePercentValue = getAveragePercentValue(average);
        layout.add(statisticsLabel, statisticsGrid, averagePercentLabel, averagePercentValue);
        return layout;
    }

    private Label getStatisticsLabel() {
        Label label = new Label();
        label.setText("Statistics");
        label.setWidth("500px");
        return label;
    }

    private Grid<Stats> getStatisticsGrid(Stats stats) {
        Grid<Stats> grid = new Grid<>();
        grid.setItems(stats);
        grid.addColumn(Stats::getOneDayStats).setHeader("One day");
        grid.addColumn(Stats::getOneWeekStats).setHeader("One week");
        grid.addColumn(Stats::getOneMonthStats).setHeader("One month");
        return grid;
    }

    private Label getAveragePercentLabel() {
        Label label = new Label();
        label.setText("Percentual profit");
        label.setWidth("500px");
        return label;

    }

    private Label getAveragePercentValue(BigDecimal average) {
        Label label = new Label();
        label.setText(average.toString());
        label.setWidth("500px");
        return label;
    }
}