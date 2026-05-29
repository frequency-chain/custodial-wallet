import {KeyUsageType} from "../repository/user_key_data.repository";
import {
  ProviderExternalUserDetail, providerExternalUserDetailRepositoryProvider
} from "../repository/provider_external_user_detail.repository";

export interface CustodialWalletDatabaseService {
  findProviderExternalUserDetailsByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId: number, publicKeyHex: string, keyUsageType: KeyUsageType): Promise<Array<ProviderExternalUserDetail>>
  saveProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): Promise<ProviderExternalUserDetail>;
}

export class TypeOrmCustodialWalletDatabaseService implements CustodialWalletDatabaseService {
  //constructor(){} TODO figure out how to inject things
  async findProviderExternalUserDetailsByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId: number, publicKeyHex: string, keyUsageType: KeyUsageType): Promise<Array<ProviderExternalUserDetail>> {
    const ProviderExternalUserDetailRepository = await providerExternalUserDetailRepositoryProvider.get();
    return ProviderExternalUserDetailRepository.findByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId, publicKeyHex, keyUsageType);
  }

  async saveProviderExternalUserDetail(providerExternalUserDetail: ProviderExternalUserDetail): Promise<ProviderExternalUserDetail> {
    const providerExternalUserDetailRepository = await providerExternalUserDetailRepositoryProvider.get()
    return providerExternalUserDetailRepository.save(providerExternalUserDetail);
  }
}

export const custodialWalletDatabaseService = new TypeOrmCustodialWalletDatabaseService();

