package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

import java.math.BigInteger;

@ScaleReader
@ScaleWriter
public class ScaleIcsMsaIdRequest {
  @Scale(ScaleType.U64.class)
  private BigInteger msaId;

  @Scale(ScaleType.String.class)
  private String nonce;

  public ScaleIcsMsaIdRequest(BigInteger msaId, String nonce) {
    this.msaId = msaId;
    this.nonce = nonce;
  }

  public ScaleIcsMsaIdRequest() {
  }

  public BigInteger getMsaId() {
    return msaId;
  }

  public void setMsaId(BigInteger msaId) {
    this.msaId = msaId;
  }

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
}
