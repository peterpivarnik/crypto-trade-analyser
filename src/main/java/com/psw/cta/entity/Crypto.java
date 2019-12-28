package com.psw.cta.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Crypto {

    private Long id;

    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal volume;
    private BigDecimal sumDiffsPerc;
    private BigDecimal sumDiffsPerc10h;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal weight;
    private Long createdAt;
    private LocalDateTime createdAtDate;
    private BigDecimal nextDayMaxPrice;
    private BigDecimal next2DayMaxPrice;
    private BigDecimal nextWeekMaxPrice;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getSumDiffsPerc() {
        return sumDiffsPerc;
    }

    public void setSumDiffsPerc(BigDecimal sumDiffsPerc) {
        this.sumDiffsPerc = sumDiffsPerc;
    }

    public BigDecimal getSumDiffsPerc10h() {
        return sumDiffsPerc10h;
    }

    public void setSumDiffsPerc10h(BigDecimal sumDiffsPerc10h) {
        this.sumDiffsPerc10h = sumDiffsPerc10h;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public void setPriceToSell(BigDecimal priceToSell) {
        this.priceToSell = priceToSell;
    }

    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
        this.priceToSellPercentage = priceToSellPercentage;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCreatedAtDate() {
        return createdAtDate;
    }

    public void setCreatedAtDate(LocalDateTime createdAtDate) {
        this.createdAtDate = createdAtDate;
    }

    public BigDecimal getNextDayMaxPrice() {
        return nextDayMaxPrice;
    }

    public void setNextDayMaxPrice(BigDecimal nextDayMaxPrice) {
        this.nextDayMaxPrice = nextDayMaxPrice;
    }

    public BigDecimal getNext2DayMaxPrice() {
        return next2DayMaxPrice;
    }

    public void setNext2DayMaxPrice(BigDecimal next2DayMaxPrice) {
        this.next2DayMaxPrice = next2DayMaxPrice;
    }

    public BigDecimal getNextWeekMaxPrice() {
        return nextWeekMaxPrice;
    }

    public void setNextWeekMaxPrice(BigDecimal nextWeekMaxPrice) {
        this.nextWeekMaxPrice = nextWeekMaxPrice;
    }
}
