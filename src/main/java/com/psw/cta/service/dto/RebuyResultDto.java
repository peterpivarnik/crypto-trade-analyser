package com.psw.cta.service.dto;

public class RebuyResultDto {

    private String clientOrderId;
    private boolean rebuySplitted;

    public RebuyResultDto(String clientOrderId) {
        this.clientOrderId = clientOrderId;
        this.rebuySplitted = false;
    }

    public RebuyResultDto(boolean shouldSplit) {
        this.clientOrderId = "";
        this.rebuySplitted = shouldSplit;
    }

    public RebuyResultDto() {
        this.clientOrderId = "";
        this.rebuySplitted = false;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public boolean isRebuySplitted() {
        return rebuySplitted;
    }

    public void setRebuySplitted(boolean rebuySplitted) {
        this.rebuySplitted = rebuySplitted;
    }

}
