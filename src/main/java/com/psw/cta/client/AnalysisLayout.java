package com.psw.cta.client;

import com.psw.cta.entity.CryptoResult;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.time.ZonedDateTime;
import java.util.List;

import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneId.systemDefault;
import static java.time.ZonedDateTime.ofInstant;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@SpringComponent
public class AnalysisLayout {

    private CacheService cacheService;

    public AnalysisLayout(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public VerticalLayout getLayout() {
        List<CryptoResult> actualCryptos = cacheService.getCryptos().getCrypto1H();
        return new VerticalLayout(getCryptoJsonGrid(actualCryptos));
    }

    private Grid<CryptoResult> getCryptoJsonGrid(List<CryptoResult> actualCryptos) {
        Grid<CryptoResult> grid = new Grid<>();
        grid.setItems(actualCryptos);
        grid.addColumn(this::getZonedDateTime).setHeader("Date");
        grid.addColumn(CryptoResult::getSymbol).setHeader("Symbol");
        grid.addColumn(CryptoResult::getCurrentPrice).setHeader("Current price");
        grid.addColumn(CryptoResult::getPriceToSell).setHeader("Price to sell");
        grid.addColumn(CryptoResult::getPriceToSellPercentage).setHeader("Percent");
        grid.setWidth("1000px");
        grid.setHeightByRows(true);
        return grid;
    }

    private String getZonedDateTime(CryptoResult cryptoResult) {
        ZonedDateTime zonedDateTime = ofInstant(ofEpochMilli(cryptoResult.getCreatedAt()), systemDefault());
        return zonedDateTime.format(ISO_LOCAL_DATE_TIME);
    }
}
