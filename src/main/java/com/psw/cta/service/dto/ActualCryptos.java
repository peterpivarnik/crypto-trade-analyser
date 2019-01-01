package com.psw.cta.service.dto;

import com.psw.cta.entity.CryptoResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ActualCryptos {

    private List<CryptoResult> crypto1H;
}
