package com.psw.cta.client.service;

import com.psw.cta.client.factory.ClientFactory;
import com.psw.cta.entity.CryptoResult;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.time.Instant;
import java.util.List;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@SpringComponent
public class AnalysisService {

    private CacheService cacheService;
    private ClientFactory clientFactory;

    public AnalysisService(CacheService cacheService, ClientFactory clientFactory) {
        this.cacheService = cacheService;
        this.clientFactory = clientFactory;
    }

    VerticalLayout getAnalysisLayout() {
        List<CryptoResult> actualCryptos = cacheService.getCryptos().getCrypto1H();
        Component layoutLabel = clientFactory.createLayoutLabel("Analysis", "100%");
        Grid<CryptoResult> cryptoJsonGrid = getCryptoJsonGrid(actualCryptos);
        return clientFactory.createVerticalLayout(layoutLabel, cryptoJsonGrid);
    }

    private Grid<CryptoResult> getCryptoJsonGrid(List<CryptoResult> actualCryptos) {
        Grid<CryptoResult> grid = new Grid<>();
        grid.setItems(actualCryptos);
        grid.addColumn(this::getLifeTime).setHeader("Life time");
        grid.addColumn(CryptoResult::getSymbol).setHeader("Symbol");
        grid.addColumn(CryptoResult::getCurrentPrice).setHeader("Current price");
        grid.addColumn(CryptoResult::getPriceToSell).setHeader("Price to sell");
        grid.addColumn(CryptoResult::getPriceToSellPercentage).setHeader("Percent");
        grid.setWidthFull();
        grid.setHeightByRows(true);
        return grid;
    }

    private String getLifeTime(CryptoResult cryptoResult) {
        long lifeTime = (Instant.now().toEpochMilli() - cryptoResult.getCreatedAt()) / 1000;
        return "" + lifeTime + " seconds";
    }
}
