package com.psw.cta.service.dto;

import com.psw.cta.entity.CryptoResult;
import elemental.html.Crypto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ActualCryptos {

    private List<CryptoResult> crypto1H;
    private List<CryptoResult> crypto2H;
    private List<CryptoResult> crypto5H;
}
