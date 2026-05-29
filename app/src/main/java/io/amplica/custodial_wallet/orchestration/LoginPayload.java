package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleGeneric;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

import java.util.Objects;
import java.util.Optional;

@ScaleReader
@ScaleWriter
public class LoginPayload {
  private String nonce;
  @ScaleGeneric(
      template = "Option<String>",
      types = {
          @Scale(ScaleType.Option.class),
          @Scale(ScaleType.String.class)
      }
  )
  private Optional<String> url;

  public LoginPayload() {
    //For Frameworks
  }

  public LoginPayload(String nonce, String url) {
    this.nonce = nonce;
    this.url = Optional.ofNullable(url);
  }

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  public Optional<String> getUrl() {
    return url;
  }

  public void setUrl(Optional<String> url) {
    this.url = url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LoginPayload)) return false;
    LoginPayload that = (LoginPayload) o;
    return getNonce().equals(that.getNonce()) && getUrl().equals(that.getUrl());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getNonce(), getUrl());
  }
}
