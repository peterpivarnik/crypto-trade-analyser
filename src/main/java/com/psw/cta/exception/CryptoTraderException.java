package com.psw.cta.exception;

/**
 * Exception to be thrown in application.
 */
public class CryptoTraderException extends RuntimeException {

  /**
   * Constructor with message as parameter.
   *
   * @param message exception message
   */
  public CryptoTraderException(String message) {
    super(message);
  }

  /**
   * Constructor with exception as parameter.
   *
   * @param e exception
   */
  public CryptoTraderException(Exception e) {
    super(e);
  }
}
