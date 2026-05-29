# Deployments

**Instructions updated on 03/26/24**

## Deployment Targets

**CW Environment** | **AWS Account** | **URL** | **API Docs**
:-----------------:|:---------------:|---------|---------------
**Dev** | Stg/Dev | https://dev-custodial-wallet.liberti.social | https://dev-custodial-wallet.liberti.social/api
**Testnet** | Prod | https://testnet.frequencyaccess.com | https://testnet.frequencyaccess.com/api
**Mainnet** | Prod | https://frequencyaccess.com | https://frequencyaccess.com/api

## General Deployment Instructions
1. Build the application (currently done via CircleCI) and record the ID of the build
2. Deploy the build (currently done via Jenkins) using the ID from step 1
3. Test (currently done via Access-Continuous-Testing on GitHub, Kibana, and our HTML pages)
    - For environments that have Smoke Tests to run (currently Testnet and Mainnet), testing can be completed by running those smoke tests.
    - For environments that don't have Smoke Tests (Devnet) or if the Smoke Tests aren't working, manually signing up or, in the case of Mainnet, using logs (currently Kibana) to verify that users are signing up/logging in successfully is a good way to test.

## Dev
1. Go to `https://app.circleci.com/pipelines/github/ProjectLibertyLabs/custodial-wallet` and find the build that you would like to deploy.
2. Click on the down arrow in the status column and copy the number next to `aws-ecr/build_and_push_image`.
3. Go to `https://jenkins.ops.liberti.social/view/Saas%20Team/` and click on `custodial-wallet-deploy-new`.
4. Click on `Build with Parameters` and change the image tag to the number you copied from CircleCI. The namespace (dev) and Slack channel should not need to change.
5. Click `Build` and wait for the Jenkins job to complete.
6. Go to `https://logs.frequency.xyz/_plugin/kibana/app/discover#/` and change the index to `eks-logs*`.
7. Search for `kubernetes.namespace:"dev" AND kubernetes.labels.app_kubernetes_io/name:"custodial-wallet”` and verify that you see a message saying `Started CustodialWalletApplicationKt`
8. In this project, open [this file](app/src/test/resources/static/dev/signup/email.html) and open it in a browser.
9. Change the email to one you haven't signed up with before (using plus addressing) and run through the signup process. You will need to go to `https://dev-custodial-wallet.liberti.social/api` and run `/api/signup/token` during the signup process.

## Testnet
1. Go to `https://app.circleci.com/pipelines/github/ProjectLibertyLabs/custodial-wallet` and find the build that you would like to deploy.
2. Click on the down arrow in the status column and copy the number next to `aws-ecr/build_and_push_image`.
3. Go to `https://jenkins.ops.liberti.social/view/Saas%20Team/` and click on `custodial-wallet-rococo-testnet`.
4. Click on `Build with Parameters` and change the image tag to the number you copied from CircleCI. The namespace (cw) and Slack channel should not need to change.
5. Click `Build` and wait for the Jenkins job to complete.
6. Run the workflow here using the `main` branch: `https://github.com/ProjectLibertyLabs/access-continuous-testing/actions/workflows/aa-mewe-smoke-tests.yml` and verify that the MeWe Staging tests pass successfully.

**NOTE: If Step 6 completed successfully, you can disregard Steps 7-10.**

7. Go to `https://logs.frequency.xyz/_plugin/kibana/app/discover#/` and change the index to `eks-logs*`.
8. Search for `kubernetes.namespace:"cw" AND kubernetes.labels.app_kubernetes_io/name:"custodial-wallet-1"` and verify that you see a message saying `Started CustodialWalletApplicationKt`
9. In this project, open [this file](app/src/test/resources/static/testnet/signup/email.html) and open it in a browser.
10. Change the email to one you haven't signed up with before (using plus addressing) and run through the signup process. You will need to go to `https://testnet.amplicaaccess.com/api` and run `/api/signup/token` during the signup process.

## Mainnet
1. Go to `https://app.circleci.com/pipelines/github/ProjectLibertyLabs/custodial-wallet` and find the build that you would like to deploy.
2. Click on the down arrow in the status column and copy the number next to `aws-ecr/build_and_push_image`.
3. Go to `https://jenkins.ops.liberti.social/view/Saas%20Team/` and click on `custodial-wallet-deploy-mainnet`.
4. Click on `Build with Parameters` and change the image tag to the number you copied from CircleCI. The namespace (cw-mainnet) and Slack channel should not need to change.
5. Click `Build` and wait for the Jenkins job to complete.
6. Run the workflow here using the `main` branch: `https://github.com/ProjectLibertyLabs/access-continuous-testing/actions/workflows/aa-mewe-smoke-tests.yml` and verify that the MeWe Production tests pass successfully.

**NOTE: If Step 6 completed successfully, you can disregard Steps 7-9.**

7. Go to `https://logs.frequency.xyz/_plugin/kibana/app/discover#/` and change the index to `eks-logs*`.
8. Search for `kubernetes.namespace:"cw-mainnet" AND kubernetes.labels.app_kubernetes_io/name:"custodial-wallet" AND message:”CustodialWalletApplicationKt”` and verify that you see a message saying `Started CustodialWalletApplicationKt`
9. Search for `kubernetes.namespace:"cw-mainnet" AND kubernetes.labels.app_kubernetes_io/name:"custodial-wallet"` and look through the logs coming in since the deployment, making sure you see success messages (200s).
