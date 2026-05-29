package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

@ScaleReader
@ScaleWriter
public class ScaleHandlePayloadRequest {
  @Scale(ScaleType.String.class)
  private String baseHandle;

  public ScaleHandlePayloadRequest(String baseHandle) {
    this.baseHandle = baseHandle;
  }

  public ScaleHandlePayloadRequest() {
    //For Frameworks
  }

  public String getBaseHandle() {
    return baseHandle;
  }

  public void setBaseHandle(String baseHandle) {
    this.baseHandle = baseHandle;
  }
}
