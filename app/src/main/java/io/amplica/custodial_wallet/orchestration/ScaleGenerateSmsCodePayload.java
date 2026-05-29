package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

@ScaleReader
@ScaleWriter
public class ScaleGenerateSmsCodePayload {
  @Scale(ScaleType.String.class)
  private String sessionId;

  public ScaleGenerateSmsCodePayload(){
    //For Frameworks
  }

  public ScaleGenerateSmsCodePayload(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
