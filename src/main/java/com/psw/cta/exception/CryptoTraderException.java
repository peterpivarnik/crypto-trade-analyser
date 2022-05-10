package com.psw.cta.exception;

/**
 * Exception to be thrown in application.
 */
public class CryptoTraderException extends RuntimeException {

  public CryptoTraderException(String message) {
    super(message);
  }

  public CryptoTraderException(Exception exception) {
    super(exception);
  }
}
