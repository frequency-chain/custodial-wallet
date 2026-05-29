import process from "process";
import {DataSource} from "typeorm";
import {join} from "path";
import {getOrThrow} from "../util/ConfigUtils";
import * as dotenv from "dotenv";
dotenv.config();

interface DbConfig {
    host: string,
    port: number,
    username: string,
    password: string,
    database: string,
    schema: string,
    poolSize: number,
}

const env = process.env
let dbConfig: DbConfig;
dbConfig = {
    host: getOrThrow(env.DB_HOST),
    port: Number(getOrThrow(env.DB_PORT)),
    username: getOrThrow(env.DB_USERNAME),
    password: getOrThrow(env.DB_PASSWORD),
    database: getOrThrow(env.DB_DATABASE),
    schema: getOrThrow(env.DB_SCHEMA),
    poolSize: Number(getOrThrow(env.DB_POOL_SIZE))
};
const entities = join(__dirname, "../repository/**/!(*.test.ts)");
const dataSource = new DataSource({
    ...dbConfig,
    type: "postgres",
    entities: [entities],
    synchronize: false,
    logging: false
});

export const getDataSource = async function getDataSource(): Promise<DataSource> {
  if(!dataSource.isInitialized) {
    return await dataSource.initialize();
  }

  return dataSource;
}