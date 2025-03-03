package com.psw.cta.utils;

import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Constants used throughout Binance's API.
 */
public class BinanceApiConstants {

  /**
   * REST API base URL.
   */
  public static final String API_BASE_URL = "https://api1.binance.com";

  /**
   * HTTP Header to be used for API-KEY authentication.
   */
  public static final String API_KEY_HEADER = "X-MBX-APIKEY";

  /**
   * Decorator to indicate that an endpoint requires an API key.
   */
  public static final String ENDPOINT_SECURITY_TYPE_APIKEY = "APIKEY";

  /**
   * Decorator to indicate that an endpoint requires a signature.
   */
  public static final String ENDPOINT_SECURITY_TYPE_SIGNED = "SIGNED";
  public static final String ENDPOINT_SECURITY_TYPE_SIGNED_HEADER = ENDPOINT_SECURITY_TYPE_SIGNED + ": #";

  /**
   * Default receiving window.
   */
  public static final long DEFAULT_RECEIVING_WINDOW = 60_000L;

  /**
   * Default ToStringStyle used by toString methods.
   * Override this to change the output format of the overridden toString methods.
   * - Example ToStringStyle.JSON_STYLE
   */
  public static final ToStringStyle TO_STRING_BUILDER_STYLE = ToStringStyle.SHORT_PREFIX_STYLE;

  private BinanceApiConstants() {
  }
}
