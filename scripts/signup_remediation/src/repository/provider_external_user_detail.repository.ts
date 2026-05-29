import {
  BeforeInsert,
  BeforeUpdate,
  Column,
  Entity,
  ManyToOne,
  PrimaryGeneratedColumn,
  JoinColumn,
  Repository
} from "typeorm";
import {ProviderExternalUser} from "./provider_external_user.repository";
import {UserAccount} from "./user_account.repository";
import {getDataSource} from "../db/dataSource";
import {KeyUsageType} from "./user_key_data.repository";
import {Provider} from "../util/ConfigUtils";

export enum UserDetailType {
  EMAIL= "EMAIL",
  PHONE_NUMBER = "PHONE_NUMBER"
}

@Entity({name: "provider_external_user_detail"})
export class ProviderExternalUserDetail {
  @PrimaryGeneratedColumn()
  id?: number;

  @ManyToOne(() => ProviderExternalUser, (providerExternalUser) => providerExternalUser.id, {eager: true})
  @JoinColumn({name: "provider_external_user_id"})
  providerExternalUser: ProviderExternalUser;

  @ManyToOne(() => UserAccount, (userAccount) => userAccount.id, {eager: true})
  @JoinColumn({name: "user_account_id"})
  userAccount: UserAccount;

  @Column({
    name: "user_detail_value",
    type: "varchar",
    length: 128
  })
  userDetailValue: string;

  @Column({
    name: "user_detail_type",
    type: "varchar",
    length: 128
  })
  userDetailType: UserDetailType;

  @Column({
    name: "user_detail_priority",
    type: "int"
  })
  userDetailPriority: number;

  @Column({
    name: "created_at",
    type: "bigint"
  })
  createdAt: string;

  @Column({
    name: "last_modified",
    type: "bigint"
  })
  lastModified: string;

  @Column({
    name: "version",
    type: "bigint"
  })
  version: string;

  @BeforeInsert()
  public setCreateDate(): void {
    this.createdAt = new Date().getTime().toString();
    this.lastModified = new Date().getTime().toString();
  }

  @BeforeUpdate()
  public setUpdateDate(): void {
    this.lastModified = new Date().getTime().toString();
  }


  constructor(id: number | undefined, providerExternalUser: ProviderExternalUser, userAccount: UserAccount, userDetailValue: string, userDetailType: UserDetailType, userDetailPriority: number, createdAt: string, lastModified: string, version: string) {
    this.id = id;
    this.providerExternalUser = providerExternalUser;
    this.userAccount = userAccount;
    this.userDetailValue = userDetailValue;
    this.userDetailType = userDetailType;
    this.userDetailPriority = userDetailPriority;
    this.createdAt = createdAt;
    this.lastModified = lastModified;
    this.version = version;
  }
}


export const providerExternalUserDetailRepositoryProvider: Provider<Repository<ProviderExternalUserDetail> & {
  findByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId: number, publicKeyHex: string, keyUsageType: KeyUsageType): Promise<Array<ProviderExternalUserDetail>>
}> = {
  async get(): Promise<Repository<ProviderExternalUserDetail> & {
    findByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId: number, publicKeyHex: string, keyUsageType: KeyUsageType): Promise<Array<ProviderExternalUserDetail>>
  }> {
    const dataSource = await getDataSource();
    const ProviderExternalUserDetailRepository = dataSource.getRepository(ProviderExternalUserDetail).extend({
      findByProviderMsaIdAndPublicKeyHexAndKeyUsageType(providerMsaId: number, publicKeyHex: string, keyUsageType: KeyUsageType): Promise<Array<ProviderExternalUserDetail>> {
        return this.createQueryBuilder("providerExternalUserDetail")
          .innerJoinAndSelect("providerExternalUserDetail.providerExternalUser", "providerExternalUser")
          .innerJoinAndSelect("providerExternalUser.userKeyData", "userKeyData")
          .innerJoinAndSelect("userKeyData.userAccount", "userAccount")
          .where("providerExternalUser.providerMsaId=:providerMsaId", {
            providerMsaId: providerMsaId
          })
          .andWhere("userKeyData.publicKeyHex=:publicKeyHex", {
            publicKeyHex: publicKeyHex
          } )
          .andWhere("userKeyData.keyUsageType=:keyUsageType", {
            keyUsageType: keyUsageType
          }).getMany()
      }
    });

    return ProviderExternalUserDetailRepository;
  }
}
