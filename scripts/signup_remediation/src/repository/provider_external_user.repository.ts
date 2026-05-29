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
import {UserKeyData} from "./user_key_data.repository";
import {getDataSource} from "../db/dataSource";
import {Provider} from "../util/ConfigUtils";

@Entity({name: "provider_external_user"})
export class ProviderExternalUser {
  @PrimaryGeneratedColumn()
  id?: number;

  @ManyToOne(() => UserKeyData, (userKeyData) => userKeyData.id, {eager: true})
  @JoinColumn({name: "user_key_data_id"})
  userKeyData: UserKeyData;

  @Column({
    name: "provider_msa_id",
    type: "bigint",
  })
  providerMsaId: string;

  @Column({
    name: "provider_external_id",
    type: "varchar",
    length: 128
  })
  providerExternalId: string;

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

  @BeforeInsert()
  public setCreateDate(): void {
    this.createdAt = new Date().getTime().toString();
    this.lastModified = new Date().getTime().toString();
  }

  @BeforeUpdate()
  public setUpdateDate(): void {
    this.lastModified = new Date().getTime().toString();
  }

  constructor(id: number, userKeyData: UserKeyData, providerMsaId: string, providerExternalId: string, createdAt: string, lastModified: string) {
    this.id = id;
    this.userKeyData = userKeyData;
    this.providerMsaId = providerMsaId;
    this.providerExternalId = providerExternalId;
    this.createdAt = createdAt;
    this.lastModified = lastModified;
  }
}

export const providerExternalUserRepository: Provider<Repository<ProviderExternalUser>> = {
  async get(): Promise<Repository<ProviderExternalUser>> {
    const dataSource = await getDataSource();
    return dataSource.getRepository(ProviderExternalUser)
  }
}
