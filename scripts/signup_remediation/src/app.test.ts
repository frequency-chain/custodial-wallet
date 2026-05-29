import {RemediationTuple, signupRemediationService} from "./service/SignupRemediationService";
import {custodialWalletDatabaseService} from "./service/CustodialWalletDatabaseService";
import {KeyUsageType} from "./repository/user_key_data.repository";
import {ss58ToHex} from "./util/ConfigUtils";

describe("App Tests", () => {
  test("executeRemediation", async () => {
    const ss58Address = "5E4V5S12gLgdMYQofmrBr5HVUNYBuNoGbt9kDqXV6CpRwTfx" //This is on testnet TODO use testcontainers
    const remediationTuples: Array<RemediationTuple> = [{
      ss58Address: ss58Address,
      contactMethods: ["+13105341234","peter.frank+13105341234@unfinished.com","peter.frank+1310534123@unfinished.com", "peter.frank+1310534124@unfinished.com","peter.frank+1310534125@unfinished.com","+18055597765","+18055597765","+18055597767"] //To Test add a new contact value here
    }];
    const publicKeyHex = ss58ToHex(ss58Address);
    await signupRemediationService.executeRemediation(remediationTuples);
    const found = await custodialWalletDatabaseService.findProviderExternalUserDetailsByProviderMsaIdAndPublicKeyHexAndKeyUsageType(9, publicKeyHex, KeyUsageType.ACCOUNT);
    expect(found.length).toEqual(remediationTuples[0].contactMethods.length);
  });
});
