package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleGeneric;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@ScaleReader
@ScaleWriter
public class ScaleAddProviderPayloadRequest {
  @Scale(ScaleType.U64.class)
  private BigInteger msaId;

  @ScaleGeneric(
    template = "Vec<U16>",
    types = {
      @Scale(ScaleType.Vec.class),
      @Scale(ScaleType.U16.class)
    }
  )
  private List<Integer> schemaIds;

  @ScaleGeneric(
    template = "Option<String>",
    types = {
      @Scale(ScaleType.Option.class),
      @Scale(ScaleType.String.class)
    }
  )
  private Optional<String> url;

  public ScaleAddProviderPayloadRequest(){
    //For Frameworks
  }

  public ScaleAddProviderPayloadRequest(BigInteger msaId, List<Integer> schemaIds, String url) {
    this.msaId = msaId;
    this.schemaIds = schemaIds;
    this.url = Optional.ofNullable(url);
  }

  public BigInteger getMsaId() {
    return msaId;
  }

  public void setMsaId(BigInteger msaId) {
    this.msaId = msaId;
  }

  public List<Integer> getSchemaIds() {
    return schemaIds;
  }

  public void setSchemaIds(List<Integer> schemaIds) {
    this.schemaIds = schemaIds;
  }

  public Optional<String> getUrl() {
    return url;
  }

  public void setUrl(Optional<String> url) {
    this.url = url;
  }
}
