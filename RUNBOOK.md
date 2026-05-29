# Runbook

This file contains guides for accomplishing various testing and maintenance tasks related to the custodial wallet.

## Testing SIWA

### Testnet via Harness

1. Open test harness HTML page in the browser. At the time of writing, the test harness HTML page is located at
  `$REPO_ROOT/custodial-wallet/app/src/test/resources/static/testnet/siwa/request.html`.
  Click on the [Test] button to initiate the Sign in process.
1. On the next Welcome page, enter your email address with a random tag in order to reuse the same email in the future.
  For example, `peter.frank+2393@projectliberty.io` where `2393` is a randomly generated number.
  Click on [Agree and Continue] button when finished.
1. On the next page, enter the verification code from the email and click on the [Agree & Continue] button.
1. On the final step page, choose your Universal Handle (e.g. `pfrank-2393`) and click on [Agree & Continue] button.
  You will be redirected to a broken `localhost` page with an authorization code in the URL.
1. Copy the value of `authorizationCode` from the URL.
1. Use the [siwa/api/payload](https://testnet.frequencyaccess.com/webjars/swagger-ui/index.html#/siwa-api-controller/retrievePayload) to exchange the copied authorization code for the signed payload (and credentials).
1. (optionally) If you have the chain running locally and need to register the user, you can submit those payloads through the [polkadot portal](https://polkadot.js.org/apps).

### Testnet via Partner Implementation

* Soar: https://testscribe.soar.com/
* MeWe: https://qa-groupl.es/

## Updating Email Templates

### Dev Environment (`Stg/Dev`)

1. Connect to VPN.
1. Visit dev environment email templates admin UI https://ses-template-manager.liberti.social/
1. Click on the edit (pencil) icon next to the template name you wish to update.
1. Make updates to the template. Test the template rendering in an online HTML viewer, ex. https://html.onlineviewer.net
1. If everything looks correct, click on the [Update] button to save your changes.
   ⚠️ Please note, at the time of writing, the templates appear to be sorted by the last updated timestamp, so their order will change after each update.
1. Repeat the steps above for other templates.

### Testnet/Mainnet Environment (`Prod`)

The email templates are shared between testnet and mainnet environments.

1. Connect to VPN
1. Visit mainnet environment email templates admin UI https://ses-template-manager.amplicaaccess.com
1. Follow the same steps as update in the dev environment above.

## Testing hCaptcha

### Locally

1. Configure `application-dev.properties`
    ```properties
    ...
    unfinished.custodial-wallet.hcaptcha.enabled=true
    unfinished.custodial-wallet.hcaptcha.site_key=6efe9c40-5d37-4750-bffe-3ec1e98c2077
    ...
    ```
1. `bootRun` the application
1. Open `127.0.0.1:8080` in a Chrome incognito window
1. Override the user agent
    1. Open the developer console
    1. Navigate to the **Network** tab
    1. Click on the Wifi & Gear icon to open the **Network Conditions** panel
    1. Uncheck 'Use browser default'
    1. Paste this custom value:
        ```
        Mozilla/5.0 (X11; Linux x86_64; rv:143.0) Gecko/20100101 Firefox/143.0
        ```
1. Interact with website flows that trip an hCaptcha check and you should now be challenged

#### Troubleshooting

- Try submitting multiple attempts in a short time period
- Try disconnecting from the VPN
