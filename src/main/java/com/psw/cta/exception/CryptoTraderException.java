package com.psw.cta.exception;

/**
 * Exception to be thrown in application.
 */
public class CryptoTraderException extends RuntimeException {

  /**
   * Default constructor.
   *
   * @param message exception message
   */
  public CryptoTraderException(String message) {
    super(message);
  }
}
