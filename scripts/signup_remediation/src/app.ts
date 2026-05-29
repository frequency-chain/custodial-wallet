import {RemediationTuple, signupRemediationService, _remediationConfig} from "./service/SignupRemediationService";
import * as fs from "fs";
import * as readline from "readline";
import { stringify } from 'csv-stringify';
import { generate } from 'csv-generate';

async function runRemediation(): Promise<void> {
  const records: Array<RemediationTuple> = [];
  const fileStream = fs.createReadStream(_remediationConfig.contactsCsvFileLocation);
  const readLineInterface = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  })

  for await (const line of readLineInterface) {
    console.log(`Line from file: ${line}`);
    const tokens = line.split(",")
    const ss58Address = tokens[0]
    const contactMethods = tokens.slice(1)
    records.push({
      ss58Address: ss58Address,
      contactMethods: contactMethods
    })
  }

  const outputRemediationTuples = await signupRemediationService.executeRemediation(records);

  stringify(outputRemediationTuples)
    .pipe(process.stdout);
}

runRemediation(); //This is an awful hack
