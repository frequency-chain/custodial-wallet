import {ProviderExternalUserDetail, UserDetailType} from "../repository/provider_external_user_detail.repository";
import {custodialWalletDatabaseService, CustodialWalletDatabaseService} from "./CustodialWalletDatabaseService";
import {KeyUsageType} from "../repository/user_key_data.repository";
import { Set } from "immutable";
import {getOrThrow, ss58ToHex} from "../util/ConfigUtils";

export interface RemediationTuple {
  ss58Address: string,
  contactMethods: Array<string>,
  success?: boolean
}

export interface RemediationConfig {
  providerMsaId: number,
  contactsCsvFileLocation: string
}

const env = process.env;
export const _remediationConfig: RemediationConfig = {
  providerMsaId: Number(getOrThrow(env.PROVIDER_MSA_ID)),
  contactsCsvFileLocation: getOrThrow(env.CONTACTS_CSV_FILE_LOCATION)
}

function determineUserDetailType(contactMethod: string): UserDetailType {
  if(contactMethod.startsWith("+")){
    return UserDetailType.PHONE_NUMBER
  }else {
    return UserDetailType.EMAIL
  }
}

export class SignupRemediationService {
  constructor(private readonly custodialWalletDatabaseService: CustodialWalletDatabaseService,
              private readonly remediationConfig: RemediationConfig) {

  }

  async executeRemediation(remediationTuples: Array<RemediationTuple>) {
    const outputRemediationTuples: Array<RemediationTuple> = [];
    for(let remediationTuple of remediationTuples) {
      try {
        const publicKeyHex = ss58ToHex(remediationTuple.ss58Address);
        const providerExternalUserDetails =
          await this.custodialWalletDatabaseService.findProviderExternalUserDetailsByProviderMsaIdAndPublicKeyHexAndKeyUsageType(
            this.remediationConfig.providerMsaId,
            publicKeyHex,
            KeyUsageType.ACCOUNT)
        if (providerExternalUserDetails.length > 0) {
          const providerExternalUserDetailsByValue: Record<string, ProviderExternalUserDetail> = {}
          for (let providerExternalUserDetail of providerExternalUserDetails) {
            providerExternalUserDetailsByValue[providerExternalUserDetail.userDetailValue] = providerExternalUserDetail
          }
          const foundContactMethods = Set(providerExternalUserDetails.map(providerExternalUserDetail => providerExternalUserDetail.userDetailValue));
          const contactMethods = Set(remediationTuple.contactMethods);
          const contactMethodsToAdd = contactMethods.subtract(foundContactMethods);
          for (let contactMethodToAdd of contactMethodsToAdd) {
            const providerExternalUserDetailFound = providerExternalUserDetailsByValue[providerExternalUserDetails[0].userDetailValue];
            const providerExternalUser = providerExternalUserDetailFound.providerExternalUser
            const now = new Date().getTime()
            const providerExternalUserDetail = new ProviderExternalUserDetail(
              undefined,
              providerExternalUser,
              providerExternalUser.userKeyData.userAccount,
              contactMethodToAdd,
              determineUserDetailType(contactMethodToAdd),
              -1,
              now.toString(),
              now.toString(),
              "0"
            )

            await custodialWalletDatabaseService.saveProviderExternalUserDetail(providerExternalUserDetail);
          }
          console.log(`Remediated ss58Address=${remediationTuple.ss58Address}`);
          outputRemediationTuples.push({
            ...remediationTuple,
            success: true
          });
        } else {
          console.log(`No ProviderExternalUserDetails found for ss58Address=${remediationTuple.ss58Address} and providerMsaId=${_remediationConfig.providerMsaId}`);
          outputRemediationTuples.push({
            ...remediationTuple,
            success: false
          });
        }
      }catch(e: any) {
        console.error(`Error occurred for ss58Address=${remediationTuple.ss58Address} and providerMsaId=${_remediationConfig.providerMsaId}`, e);
        outputRemediationTuples.push({
          ...remediationTuple,
          success: false
        });
      }
    }

    return outputRemediationTuples;
  }
}
export const signupRemediationService = new SignupRemediationService(custodialWalletDatabaseService, _remediationConfig);
