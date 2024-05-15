package com.psw.cta.exception;

/**
 * An exception which can occur while invoking methods of the Binance API.
 */
public class BinanceApiException extends RuntimeException {

  /**
   * Instantiates a new binance api exception.
   *
   * @param message the message
   */
  public BinanceApiException(String message) {
    super(message);
  }

  /**
   * Instantiates a new binance api exception.
   *
   * @param cause the cause
   */
  public BinanceApiException(Throwable cause) {
    super(cause);
  }

  /**
   * Instantiates a new binance api exception.
   *
   * @param message the message
   * @param cause   the cause
   */
  public BinanceApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
