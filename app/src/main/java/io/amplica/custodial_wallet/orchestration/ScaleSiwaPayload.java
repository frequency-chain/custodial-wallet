package io.amplica.custodial_wallet.orchestration;

import com.strategyobject.substrateclient.scale.ScaleType;
import com.strategyobject.substrateclient.scale.annotation.Scale;
import com.strategyobject.substrateclient.scale.annotation.ScaleGeneric;
import com.strategyobject.substrateclient.scale.annotation.ScaleReader;
import com.strategyobject.substrateclient.scale.annotation.ScaleWriter;

import java.util.List;
import java.util.Optional;

@ScaleReader
@ScaleWriter
public class ScaleSiwaPayload {
  @Scale(ScaleType.String.class)
  private String callback;

  @ScaleGeneric(template = "Vec<U16>", types = {@Scale(ScaleType.Vec.class), @Scale(ScaleType.U16.class)})
  private List<Integer> permissions;

  @ScaleGeneric(
      template = "Option<str>",
      types = {
          @Scale(ScaleType.Option.class),
          @Scale(ScaleType.String.class)
      })
  private Optional<String> userIdentifierAdminUrl;

  public ScaleSiwaPayload(String callback, List<Integer> permissions, String userIdentifierAdminUrl) {
    this.callback = callback;
    this.permissions = permissions;
    this.userIdentifierAdminUrl = Optional.ofNullable(userIdentifierAdminUrl);
  }

  public ScaleSiwaPayload() {
    // For Frameworks
  }

  public String getCallback() {
    return callback;
  }

  public void setCallback(String callback) {
    this.callback = callback;
  }

  public List<Integer> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<Integer> permissions) {
    this.permissions = permissions;
  }

  public Optional<String> getUserIdentifierAdminUrl() {
    return userIdentifierAdminUrl;
  }

  public void setUserIdentifierAdminUrl(Optional<String> userIdentifierAdminUrl) {
    this.userIdentifierAdminUrl = userIdentifierAdminUrl;
  }
}
