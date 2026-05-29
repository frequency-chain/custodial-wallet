import {BeforeInsert, BeforeUpdate, Column, Entity, PrimaryGeneratedColumn, Repository} from "typeorm";
import {getDataSource} from "../db/dataSource";
import {Provider} from "../util/ConfigUtils";

@Entity({name: "user_account"})
export class UserAccount {
  @PrimaryGeneratedColumn()
  id?: number;

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


  constructor(id: number, createdAt: string, lastModified: string) {
    this.id = id;
    this.createdAt = createdAt;
    this.lastModified = lastModified;
  }
}

export const userAccountRepositoryProvider: Provider<Repository<UserAccount>> = {
  async get(): Promise<Repository<UserAccount>> {
    const dataSource = await getDataSource();
    return dataSource.getRepository(UserAccount);
  }
}
