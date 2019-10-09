package com.psw.cta.service.dto;

import com.psw.cta.entity.CryptoResult;

import java.util.List;

public class ActualCryptos {

    private List<CryptoResult> crypto1H;

    public ActualCryptos(List<CryptoResult> crypto1H) {
        this.crypto1H = crypto1H;
    }

    public List<CryptoResult> getCrypto1H() {
        return crypto1H;
    }
}
