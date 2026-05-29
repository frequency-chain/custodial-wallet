# Custodial Wallet Application

This application will hold DSNP consumer public/private key pairs for users that haven't opted to use the standalone wallet

## Running Locally

### Install Prerequisites

Before you can build the app you will need to:

1. Setup GitHub credentials (for fetching private dependencies)

   1. Create a classic [GitHub Personal Access Token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens#creating-a-personal-access-token-classic) (e.g. `CUSTODIAL_WALLET_PRIVATE_REPOS_ACCESS`) and grant it `read:packages` permissions so the app can pull dependencies from GitHub Maven during build.
   1. Define environment variables with your credentials (e.g., in `~/.profile`)
      ```bash
      export GITHUB_ACTOR=<your github username>
      export GITHUB_TOKEN=<personal access token>
      ```

1. Clone [substrate-client-java](https://github.com/LibertyDSNP/substrate-client-java) and follow instruction in the repo `README` to build and publish it to your local Maven.

1. Clone [saas-frequency-client](https://github.com/ProjectLibertyLabs/saas-frequency-client) and follow instruction in the repo `README` to build and publish it to your local Maven.

1. Install local tools
   - Install the [NodeJS plugin](https://github.com/asdf-vm/asdf-nodejs)
   - Run `asdf install`

### Application Startup

The following steps are to get the app working and ready to be interacted with:

1. Copy `app/src/main/resources/application.properties.sample` to `app/src/main/resources/application-dev.properties`
    - You can override any values in this file as needed.
2. Add your AWS Credentials to `application-dev.properties`.
    - Make sure your region is set to `us-east-2`. For instructions on how to obtain AWS credentials, see [Obtain AWS User Credentials Wiki page](https://github.com/ProjectLibertyLabs/custodial-wallet/wiki/Obtain-AWS-User-Credentials).
    - Also, note that these AWS credentials **change every 12 hours**, so you will need to repeat this process every day to run the app locally.
3. Run `docker compose up` to fire up the default dependency stack (e.g. Postgres, Redis, Localstack, Frequency chain and Account Gateway Service). Please note that the Account Gateway Service is the only Gateway service that is currently started as part of the default dependency stack.
    - If you also want to start the Graph Gateway Service as part of the dependency stack, it can be started with the `graph` profile, i.e. `docker compose --profile graph up`
4. Create an MSA ID for the Alice test account. (this account is used by the test harnesses for manual testing):
   1. Go to `https://polkadot.js.org/apps` in a browser.
   2. Connect the app to the local Frequency node you have running. To do so, click on _DEVELOPMENT>Local Node_ in the left nav menu and then click on "Switch" button at the top. This should load Frequency Development (No Relay) interface.
   3. Navigate to the _Developer>Extrinsics_ section
   4. `ALICE` should already be set by default in the account section. If not, set `ALICE`.
   5. Set the system to `msa` in the extrinsics dropdown and the command next to it to `create()`.
   6. Finally, hit `Submit Transaction` and then `Sign and Submit` in the next popup.
5. Create a Provider for the Alice test account.
   1. Using similar steps to above, however, this time go to _Developer>Sudo_ section, 
   2. Set the `Runtime Call` to `msa` and then select `createProviderViaGovernanceV2()`.
   3. Set the `providerKey` to `ALICE`
   4. Set the `defaultName` to `MeWe` (it could really be anything)
   5. Set the `defaultLogo250100PngCid` to `bafkreidgvpkjawlxz6sffxzwgooowe5yt7i6wsyg236mfoks77nywkptdq` (any valid CID will work)
   6. Submit the transaction similar to above
6. Stake tokens for the Alice account to get Capacity
   1. Use above as a reference, switch extrinsic to `capacity` pallet and the `stake()` command.
   2. Set the target to 1 (this should be the MSAID of alice) and the stake amount to 10000000000 (10 trillion)
   3. Submit the signed transaction similar to above
7. If using Localstack (you are if you just booted up the docker compose with no changes):
   1. Configure AWS profile for use with localstack if you don't have it configured yet.
      ```sh
      aws configure set aws_access_key_id test --profile default
      aws configure set aws_secret_access_key test --profile default
      aws configure set region us-east-2 --profile default
      ```
   2. Make sure `application-dev.properties` has the following values:
       ```properties
       unfinished.custodial-wallet.aws-kms.service_endpoint=http://localhost:4566
       unfinished.custodial-wallet.aws-kms.key_alias=alias/custodial_wallet_dev
       ```
      (Make sure to check your region. It's localstack, so you only need to make sure it matches.)
   3. Run `aws --endpoint-url=http://localhost:4566 kms create-key --tags TagKey=Purpose,TagValue=Test --description "Test key"`
      - Note: You may need to first run `aws sso login` before this will work
   4. Run `aws --endpoint-url=http://localhost:4566 kms create-alias --alias-name alias/custodial_wallet_dev --target-key-id {KEY_ID}` where KEY_ID can be found in the metadata from the previous command. Note, this command has no output.
8. Use `./gradlew bootRun` to fire up the app

#### SIWA Configuration Seeding

If you need to interact with the SIWA flow locally you will need to register a provider organization and application in our database:

1. Grab the value of `unfinished.custodial-wallet.admin.shared.secret` from `application-dev.properties` (currently `75Dn0248y03mThX1297d`)
2. Open the [Swagger docs](http://localhost:8080/api)
3. Navigate to the `POST` `/api/admin/organization` endpoint and submit the following payload—making sure the MSA ID is the same as for your local test provider:
    ```json
    {
      "msaIds": [ 1 ],
      "displayName": "Example Provider",
      "shortcode": "example",
      "whitelistedOrigins": [
        {
          "scheme": "http",
          "domain": "example.com"
        }
      ],
      "assets": {
        "BRAND_LOGO": {
          "url": "http://localhost:8080/img/favicon/favicon-v2.svg"
        }
      }
    }
    ```
    > Note: The `whitelistedOrigins` is generally irrelevant for local and non-mainnet envs due to `unfinished.custodial-wallet.whitelist.provider.localhost.allowed=true` allowing any loopback address.

4. Navigate to the `PUT` `/api/admin/provider/msa/{providerMsaId}` endpoint and submit the following payload—making sure the `organizationId` is the same as the entity created in the prior step:
    ```json
    {
      "organizationId": 1,
      "providerApplications": [
        {
          "verifiedCredentialUrl": "http://localhost:8080/example.json",
          "displayName": "Example Application",
          "shortcode": "example-app",
          "whitelistedOrigins": [
            {
              "scheme": "http",
              "domain": "example.com"
            }
          ],
          "assets": {
            "BRAND_LOGO": {
              "url": "http://localhost:8080/img/favicon/favicon-v2.svg"
            }
          }
        }
      ]
    }
    ```
    > Note: The `verifiedCredentialUrl` is currently arbitrary—we just treat it as an identifier for an application.

### Troubleshooting

#### Postgres Errors

Make sure you do not have a local database running with:

```
lsof -n -i:5432 | grep LISTEN
```

Stop the local database:

```
brew services stop postgresql
```

## Working with Typescript (i.e., Passkey Wallet)

We have set up [tslint](https://typescript-eslint.io) for linting and [prettier](https://prettier.io) for formatting our
TS source files in `app/source/main/ts`.

These are run automatically when the gradle `assemble` task runs.

### IntelliJ Integrations

Make sure IntelliJ is respecting our TSLint and Prettier configs for a smooth experience by enabled the automatic
configuration options:

1. Open **Settings**
2. Navigate to **Languages & Frameworks › JavaScript › Prettier** and make sure it is set to `Automatic Prettier configuration`
3. Navigate to **Languages & Frameworks › JavaScript › Code Quality Tools › ESLint** and make sure it is set to `Automatic ESLint configuration`
4. Navigate to **Languages & Frameworks › TypeScript › TSLint** and make sure it is set to `Automatic TSLint configuration`

For the most seamless experience, check `Run on save` for **Prettier** and **ESLint**.

## Using Playwright (E2E testing)

Playwright is used to run simulation tests in various browser engines in order to validate
the behavior of the web pages in the project.

- To open the code generation application to create a test interactively use the `codegen` command:

  ```
  ./gradlew playwright --args="codegen [URL]"
  ```

  _`url` is optional. Read docs [here](https://playwright.dev/java/docs/codegen-intro)._

- To run tests with the visual debugger set `PWDEBUG=1`:

  ```
  PWDEBUG=1 ./gradlew test
  ```

  _Read docs [here](https://playwright.dev/java/docs/debug)_

- In order to run tests in parallel, see the guide on [Multithreading](https://playwright.dev/java/docs/multithreading).

## Automatic Restart

Using Spring boot Devtools gives us the ability to restart specific parts of the application when certain actions occur.
This is mainly split into two main groups, Classpath file restarting, and static file reloading

### Hot Restarting

Spring Boot Devtools provides a mechanism for allowing us to hot restart the server whenever classpath files change.
If you are not using IntelliJ then this likely works out of the box.

If you are using IntelliJ you will need to enable specific options in Intellij to get this working with a few more
being optional for managing the intervals of reloads.

- First required option to enable: `Settings -> Build, Execution, Deployment -> Compiler -> [x] Build project automatically`
- Second required option to enable: `Settings -> Advanced Settings -> Compiler -> [x] Allow auto-make to start...`
- Optional fields for intellij can be found in the registry (accessing this varies by OS, google it):
  ```
  compiler.document.save.trigger.delay: 100
  compiler.automake.postpone.when.idle.less.than: 2000
  compiler.automake.trigger.delay: 50
  compiler.document.save.enabled: true
  compiler.automake.allow.when.app.running: true
  ```

### Hot Reloading

The second part of Automatic Restart is hot reloading of the static and template files, which are handled differently than the classpath
kotlin/java files. You will need to copy into your application-dev.properties file the following lines (some of these are
currently defaults, but have changed in the past apparently):

```properties
spring.thymeleaf.cache=false
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.prefix=file:src/main/resources/templates/
spring.web.resources.static-locations=file:src/main/resources/static/
spring.web.resources.cache.period=0
```

This will allow thymeleaf templates as well as our css and js files to be reloaded. however this doesn't work for sass.

### SASS Compiling and Reloading

As of now, time boxing on this has prevented sass from being fully automated, although it should be possible with more
time, for now, you can cause Sass to compile with a gradle command, which will update the css files and cause a reload.

To compile and reload changes to Sass: `./gradlew app:compileSass`

### Hot Reloading Issues

Hot reloading is fraught with potential errors. Please add them here as we encounter them

- **SilentErrorException**: This is how hot reloading prompts itself to restart, however, this is a thrown exception.
  We have a (janky) catch for this exception to allow the custodial wallet to keep running. However, if you decide to do
  a hot reload while using an IDE's debug mode this will almost always trigger a breakpoint stop. You'll have to add a
  manual exception for it or just hit continue every time.

NOTE: Hot reloading is only enabled for the dev profile, which should be all that's needed, but just keep it in mind.

## Optimizing Build Times

Developers can experiment with adding the following flags to their local
`~/.gradle/gradle.properties` file in order to improve build times:

```properties
io.amplica.custodial_wallet.parallel_tests.enabled=true
```

Instructs gradle to run multiple test executors in parallel. See [docs](https://docs.gradle.org/current/userguide/performance.html#a_run_tests_in_parallel).

```properties
org.gradle.parallel=true
```

Tells gradle to execute multiple tasks from different sub-projects in parallel. See [docs](https://docs.gradle.org/current/userguide/performance.html#sec:enable_parallel_execution).

```properties
org.gradle.caching=true
```

Speeds up repeat builds even after running `clean` and wiping incremental outputs. See [docs](https://docs.gradle.org/current/userguide/performance.html#enable_build_cache).

## Sign in with Access (SIWA) Endpoints

There is currently a single entry endpoint for SIWA, the "start" endpoint. The endpoint is split into two basic steps.
Submitting a SIWA request with a POST. Then, you will be given a location you can access with a sessionid that will be
provided with the POST response.

### Start

To begin the SIWA flow you will use the `siwa/api/request` endpoint. You will need to POST the following `SignedSiwaRequest`
JSON payload to the endpoint. I recommend using the swagger api located at `/api` to help make this easier. The payload
you will need will look like the follow (if you want to use a different payload you will need to re-generate the signature):

```json
{
  "requestedSignatures": {
    "publicKey": {
      "encodedValue": "BauKu2iL4fncgfy22YSLGc1aDLpyuUUe5z8yNF2pDtLNr4E",
      "encoding": "BASE58",
      "format": "SS58",
      "type": "SR25519"
    },
    "signature": {
      "algo": "SR25519",
      "encoding": "BASE16",
      "encodedValue": "0x0eb4e27e6d093afe7442f09f55eb20bde1a1025ead7d51d5beb5ef6e1e0e1c5b933d7fceb40ce5a0634e5629d88602d607c1dfcda805b0b975dc4b95ca8bc780"
    },
    "payload": {
      "nonce": "test-nonce",
      "callback": "http://localhost:57168",
      "permissions": [5, 7, 8, 9, 10],
      "userIdentifierAdminUrl": "https://mewe.com"
    }
  },
  "requestedCredentials": [
    {
      "anyOf": [
        {
          "type": "VerifiedEmailAddressCredential",
          "hash": ["bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi"]
        }
      ]
    }
  ],
  "applicationContext": {
    "url": "https://schemas.mewe.com/mewe.json"
  }
}
```

Once you've submitted the request you should receive back a location. That location includes the session id. Using a web
browser of your choice you can plug in that location to get the siwa start page for your payload. From here, you should
be able to simply follow the siwa steps.

It is worth noting, that for the final step to receive the signed payloads you will once again need to use the swagger docs.
using the `siwa/api/payload` endpoint you can plug in the session id and auth code to get the signed payloads.

### Website Login

1. Signup for an account through SIWA.
2. Now login through the site like normal using the same identifier.

# Matomo
In the custodial wallet there is a directory containing the config and docker-compose for standing up a Matomo instance. Once stood up it can be used to locally test Matomo
1. Add to your `application-dev.properties` for `unfinished.custodial-wallet.matomo.url` the value `http://localhost:8088/`
2. There is an include for Matomo in our `dockercompuse.yml`
3. Use `docker-compose --profile matomo up` to bring up the instance
4. navigate to `localhost:8088` to finish setup of the instance. Note the website URL should be `http://localhost:8088/`
5. Now, when using the website or siwa flows, information will automatically go to the local matomo instance.

# Scripts

There are scripts included in the scripts directory that can be used to make some tasks easier/faster. Some of them require running python in a virtual environment. You will need to install python(`brew install python`) and virtualenv (`pip install virtualenv`) on your machine before following the steps below.

- To create a virtual python environment that will download the needed dependencies go to the directory containing the script you want to run and use the following command: `virtualenv venv && source venv/bin/activate && pip install -r requirements.txt`
  1. `virtualenv venv` will create a virtual env in a new directory `venv`
  2. `source .env/bin/activate` will set that new virtual environment as your active environment
  3. `pip install -r requirements.txt` will install the required dependencies to that virtual environment from the requirements.txt file in the project.
  4. After using this your command prompt should show you are working in that newly created virtual environment.
- To deactivate the virtual environment use the following command: `deactivate`
  1. This is an alias that only works inside the virtual environment.
  2. If the above command doesn't work run `source deactivate`
- To update the requirements.txt file (normally due to adding a new script) run the following command: `pip freeze > requirements.txt`
  1. This only works if you've already installed the new dependencies to your current virtual environment.
- To configure the virtual environment with IntelliJ to stop it complaining and allow running the script through IntelliJ:
  1. Download the python plugin for IntelliJ
  2. Configure a Python SDK that targets your virtual environment in File > Project Structure > SDK Platform
  3. Create a module for the scripts that uses that Python SDK in File > Project Structure > Modules | Add > Import Module > folder containing script selected > create from existing source > select SDK > set SDK in dependencies

#### DeleteUsers

These scripts will take a csv style list of users and remove them from our system as well as retire their msa and handle permanently. It will then provide output in a result csv specified.

1. Set up the python environment with the above python instructions
2. Move your user csv into the deleteUsers directory (or use the sample one)
3. Set the environment variable for the URL Base `export DELETE_USER_URL_BASE=[urlbase]` which may look like this `export DELETE_USER_URL_BASE=localhost:8080`
4. Set the environment variable for the Shared Admin Secret `export DELETE_USER_SHARED_SECRET=[sharedsecret]` which may look like this `export DELETE_USER_SHARED_SECRET=ds+g83mn/sig38g4`
5. There are now two commands to run which must be run in separate blocks:
   - call the script: `python3 revokeDelegationAndHandle.py [input-csv-here] [output-csv-here]` which may look like this `python3 revokeDelegationAndHandle.py usersToRemove.csv removalStatus.csv`
   - call the script: `python3 deleteUserAndRetireMsa.py [input-csv-here] [output-csv-here]` which may look like this `python3 deleteUserAndRetireMsa.py usersToRemove.csv removalStatus.csv`

# Troubleshooting

- If you encounter issues with aws credentials when setting up local stack for running the custodial wallet locally

  1. Type `aws configure`
  2. Enter any value for AWS Access Key ID and AWS Secret Access Key
  3. Set default region to `us-east-2`
  4. Put nothing in the output format option and hit Enter
     Build

- Custodial wallet has dependency on `graph-sdk` project which is located at `https://github.com/LibertyDSNP/graph-sdk`.
  In case of failures regarding the usage of this dependency for your local development you should run the following
  commands
  1. `git clone git@github.com:LibertyDSNP/graph-sdk.git`
  2. `cd graph-sdk`
  3. `make build-jni`
  4. `cd java`
  5. `./gradlew clean build publishToMavenLocal`

# Deployments

Deployment instructions can be found in [DEPLOYMENT.md](DEPLOYMENT.md)

# How-to Guides

Instructions for accomplishing various tasks can be found in [RUNBOOK.md](RUNBOOK.md)

# Appendix

This section contains reference material that may be needed in other areas of the README
