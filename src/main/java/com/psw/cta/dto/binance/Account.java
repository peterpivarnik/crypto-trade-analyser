package com.psw.cta.dto.binance;

import static com.psw.cta.utils.BinanceApiConstants.TO_STRING_BUILDER_STYLE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Account information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {

  /**
   * Maker commission.
   */
  private int makerCommission;

  /**
   * Taker commission.
   */
  private int takerCommission;

  /**
   * Buyer commission.
   */
  private int buyerCommission;

  /**
   * Seller commission.
   */
  private int sellerCommission;

  /**
   * Whether or not this account can trade.
   */
  private boolean canTrade;

  /**
   * Whether or not it is possible to withdraw from this account.
   */
  private boolean canWithdraw;

  /**
   * Whether or not it is possible to deposit into this account.
   */
  private boolean canDeposit;

  /**
   * Last account update time.
   */
  private long updateTime;

  /**
   * List of asset balances of this account.
   */
  private List<AssetBalance> balances;

  public int getMakerCommission() {
    return makerCommission;
  }

  public void setMakerCommission(int makerCommission) {
    this.makerCommission = makerCommission;
  }

  public int getTakerCommission() {
    return takerCommission;
  }

  public void setTakerCommission(int takerCommission) {
    this.takerCommission = takerCommission;
  }

  public int getBuyerCommission() {
    return buyerCommission;
  }

  public void setBuyerCommission(int buyerCommission) {
    this.buyerCommission = buyerCommission;
  }

  public int getSellerCommission() {
    return sellerCommission;
  }

  public void setSellerCommission(int sellerCommission) {
    this.sellerCommission = sellerCommission;
  }

  public boolean isCanTrade() {
    return canTrade;
  }

  public void setCanTrade(boolean canTrade) {
    this.canTrade = canTrade;
  }

  public boolean isCanWithdraw() {
    return canWithdraw;
  }

  public void setCanWithdraw(boolean canWithdraw) {
    this.canWithdraw = canWithdraw;
  }

  public boolean isCanDeposit() {
    return canDeposit;
  }

  public void setCanDeposit(boolean canDeposit) {
    this.canDeposit = canDeposit;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(long updateTime) {
    this.updateTime = updateTime;
  }

  public List<AssetBalance> getBalances() {
    return balances;
  }

  public void setBalances(List<AssetBalance> balances) {
    this.balances = balances;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, TO_STRING_BUILDER_STYLE)
        .append("makerCommission", makerCommission)
        .append("takerCommission", takerCommission)
        .append("buyerCommission", buyerCommission)
        .append("sellerCommission", sellerCommission)
        .append("canTrade", canTrade)
        .append("canWithdraw", canWithdraw)
        .append("canDeposit", canDeposit)
        .append("updateTime", updateTime)
        .append("balances", balances)
        .toString();
  }
}
