package com.psw.cta.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Desired response type of NewOrder requests.
 *
 * @see NewOrderResponse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum NewOrderResponseType {
  ACK, RESULT, FULL
}

