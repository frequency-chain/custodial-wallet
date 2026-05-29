package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

import java.math.BigInteger;

@ScaleReader
@ScaleWriter
public class ScaleIcsContextItemKeyPayload {
  @Scale(ScaleType.U64.class)
  private BigInteger msaId;
  private String contextItemId;
  private String nonce;

  public ScaleIcsContextItemKeyPayload() {
    // For Frameworks
  }

  public ScaleIcsContextItemKeyPayload(BigInteger msaId, String contextItemId, String nonce) {
    this.msaId = msaId;
    this.contextItemId = contextItemId;
    this.nonce = nonce;
  }

  public BigInteger getMsaId() {
    return msaId;
  }

  public void setMsaId(BigInteger msaId) {
    this.msaId = msaId;
  }

  public String getContextItemId() {
    return contextItemId;
  }

  public void setContextItemId(String contextItemId) {
    this.contextItemId = contextItemId;
  }

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
}
