package com.psw.cta.client;

import com.psw.cta.rest.dto.CryptoJson;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CryptoService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route
public class MainView extends VerticalLayout {


    public MainView(@Autowired CryptoService cryptoService) {


        Label mainLabel = getMainLabel();
        HorizontalLayout tablesLayout = getTablesLayout(cryptoService);
        add(mainLabel, tablesLayout);

    }

    private HorizontalLayout getTablesLayout(CryptoService cryptoService) {
        HorizontalLayout layout = new HorizontalLayout();
        VerticalLayout actualCryptoLayout = getActualCryptoLayout(cryptoService);
        VerticalLayout statisticsLayout = getStatisticsLayout(cryptoService);
        layout.add(actualCryptoLayout, statisticsLayout);
        return layout;
    }

    private VerticalLayout getActualCryptoLayout(CryptoService cryptoService) {
        VerticalLayout layout = new VerticalLayout();
        Label actualCryptoLabel = getActualCryptoLabel();
        Grid<CryptoJson> cryptoJsonGrid = getCryptoJsonGrid(cryptoService);
        layout.add(actualCryptoLabel, cryptoJsonGrid);
        return layout;
    }


    private VerticalLayout getStatisticsLayout(CryptoService cryptoService) {
        VerticalLayout layout = new VerticalLayout();
        Label actualCryptoLabel = getStatisticsLabel();
        Grid<Stats> cryptoJsonGrid = getStatisticsGrid(cryptoService);
        layout.add(actualCryptoLabel, cryptoJsonGrid);
        return layout;
    }

    private Label getStatisticsLabel() {
        Label label = new Label();
        label.setText("Statistics");
        label.setWidth("500px");
        return label;
    }

    private Grid<Stats> getStatisticsGrid(CryptoService cryptoService) {
        Stats stats = cryptoService.getStats();
        Grid<Stats> grid = new Grid();
        grid.setItems(stats);
        grid.addColumn(Stats::getOneDayStats).setHeader("One day");
        grid.addColumn(Stats::getOneWeekStats).setHeader("One week");
        grid.addColumn(Stats::getOneMonthStats).setHeader("One month");
        grid.setWidth("500px");
        return grid;
    }


    private Label getActualCryptoLabel() {
        Label messageLabel = new Label();
        messageLabel.setText("Actual cryptos");
        messageLabel.setWidth("500px");
        return messageLabel;
    }

    private Label getMainLabel() {
        Label messageLabel = new Label();
        messageLabel.setText("Crypto trade analyser");
        messageLabel.setWidth("500px");
        return messageLabel;
    }

    private Grid<CryptoJson> getCryptoJsonGrid(CryptoService cryptoService) {
        List<CryptoJson> actualCryptos = cryptoService.getActualCryptos();
        Grid<CryptoJson> grid = new Grid();
        grid.setItems(actualCryptos);
        grid.addColumn(CryptoJson::getDate).setHeader("Date");
        grid.addColumn(CryptoJson::getSymbol).setHeader("Symbol");
        grid.addColumn(CryptoJson::getCurrentPrice).setHeader("Current price");
        grid.addColumn(CryptoJson::getPriceToSell).setHeader("Price to sell");
        grid.addColumn(CryptoJson::getPercentage).setHeader("Percent");
        grid.setWidth("1000px");
        return grid;
    }
}