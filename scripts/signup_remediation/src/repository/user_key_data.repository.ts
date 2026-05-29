import {
  BeforeInsert,
  BeforeUpdate,
  Column,
  Entity,
  ManyToOne,
  PrimaryGeneratedColumn,
  JoinColumn, Repository
} from "typeorm";
import {UserAccount} from "./user_account.repository";
import {getDataSource} from "../db/dataSource";
import {Provider} from "../util/ConfigUtils";


export enum KeyPairType {
  ED25519 = "Ed25519",
  SR25519 = "Sr25519",
  X25519 = "X25519"
}

export enum KmsEncryptionAlgorithm {
  SYMMETRIC_DEFAULT = "SYMMETRIC_DEFAULT"
}

export enum KeyUsageType {
  ACCOUNT = "ACCOUNT",
  GRAPH = "GRAPH"
}

@Entity({name: "user_key_data"})
export class UserKeyData {
  @PrimaryGeneratedColumn()
  id?: number;

  @ManyToOne(() => UserAccount, (userAccount) => userAccount.id, {eager: true})
  @JoinColumn({name: "user_account_id"})
  userAccount: UserAccount

  //  @Column({ name: "transaction_hash", type: "varchar", length: 1028 })
  @Column({
    name: "public_key_hex",
    type: "text"
  })
  publicKeyHex: string

  @Column({
    name: "encrypted_private_key_hex",
    type: "text"
  })
  encryptedPrivateKeyHex: string

  @Column({
    name: "encrypted_private_key_type",
    type: "varchar",
    length: 128
  })
  encryptedPrivateKeyType: KeyPairType

  @Column({
    name: "kms_encryption_key_id",
    type: "varchar",
    length: 128
  })
  kmsEncryptionKeyId: string

  @Column({
    name: "kms_encryption_key_id_type",
    type: "varchar",
    length: 128
  })
  kmsEncryptionKeyIdType: KmsEncryptionAlgorithm

  @Column({
    name: "key_usage_type",
    type: "varchar",
    length: 128
  })
  keyUsageType: KeyUsageType

  @Column({
    name: "created_at",
    type: "bigint"
  })
  createdAt: string

  @Column({
    name: "last_modified",
    type: "bigint"
  })
  lastModified: string

  @BeforeInsert()
  public setCreateDate(): void {
    this.createdAt = new Date().getTime().toString();
    this.lastModified = new Date().getTime().toString();
  }

  @BeforeUpdate()
  public setUpdateDate(): void {
    this.lastModified = new Date().getTime().toString();
  }

  constructor(id: number,
              userAccount: UserAccount,
              publicKeyHex: string,
              encryptedPrivateKeyHex: string,
              encryptedPrivateKeyType: KeyPairType,
              kmsEncryptionKeyId: string,
              kmsEncryptionKeyIdType: KmsEncryptionAlgorithm,
              keyUsageType: KeyUsageType,
              createdAt: string,
              lastModified: string) {
    this.id = id;
    this.userAccount = userAccount;
    this.publicKeyHex = publicKeyHex;
    this.encryptedPrivateKeyHex = encryptedPrivateKeyHex;
    this.encryptedPrivateKeyType = encryptedPrivateKeyType;
    this.kmsEncryptionKeyId = kmsEncryptionKeyId;
    this.kmsEncryptionKeyIdType = kmsEncryptionKeyIdType;
    this.keyUsageType = keyUsageType;
    this.createdAt = createdAt;
    this.lastModified = lastModified;
  }
}

export const userKeyDataRepositoryProvider: Provider<Repository<UserKeyData>> = {
  async get(): Promise<Repository<UserKeyData>> {
    const dataSource = await getDataSource()
    return dataSource.getRepository(UserKeyData);
  }
}
