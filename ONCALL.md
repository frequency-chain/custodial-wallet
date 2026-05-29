# Oncall Instructions
For all of these instructions the oncall is the ultimate fallback and owner of all issues that come into the Saas related
world. Often other's may swoop in and take care of it because of timezone or other reasons but at the end of the day anything that
hasn't been handled is on the oncall to clean up and herd out the door to a resolution.
## Basic Flow of Handling PagerDuty Alert
1. Alert comes in, you shoudl Ack ASAP, BEFORE you look into the issue
1. Blast this chat to say you ack’d ot and are looking into it. You can basically use this chat as your scratch pad of what you are doing to keep people informed (also for resolving notes later)
1. After you triage the issue you can:
   1. Escalate it up to the next tier (one day that will be me but right now I’m on the same tier as you guys) which will alert that person
   1. If you don’t escalate and can handle it on your own Resolve it when it’s done and add some notes (not sure where notes are in the app blut on the site it’s a text area on the right)
      1. Also, if the issue came in through sentry resolve that one as well. For lower environments if it’s just a noisy thing you can leave it unresolved but for mainnet we want most everything resolved (I’m sure they’ll be an exception here or there because of noise but we should try not to do that).

The reality is often you will just be alerting devops to what the issue is because they may need to fix it, so really we are just auditing what neesd to be done; obviously this is all context dependent. (edited) 

## Dealing with Outside Support Requests (Non Pager Duty)
Barring these being us being partially or fully down in a way that somehow PagerDuty doesn't detect these aren't firedrills.
These are things that should be taken care of as soon as you can but nothing that should be a "wake up and deal with" now
level of quality of service
### mewe-amplica-test-tmp channel
When oncall you should make sure you do not miss messages in this chat, this may require changes to how you are notified
in a given channel, whatever it is configure your Slack in order for you to handle it. This channel is often troubleshooting
pre-release bugs and new architecture/decisions and asks. You should check this channel first thing in your day to as MeWe
is in Poland so there may be many asks in here. But in general:
1. Slack has no "ack" type feature so use the eyeball emojis so that you are telling MeWe and also other's on our team that you are looking into the issue/ask
2. When you are done you should respond in thread with whatever the outcome is, this will be context specific
### mewe-amplica-support channel
Much like the previous chat you need to make sure you do not miss messages in this chat, update your settings accordingly.
This channel is requests from MeWe Support to figure out what's going on. This channel has both support and MeWe Engineering.
If an issue is on the MeWe side say so and potentially tag Bogu or Piotr if you have to but I'd just respond in thread to 
the support person and let them handle it as they are on the MeWe side. The Quality of Service in this channel is slightly 
better than `mewe-amplica-test-tmp channel` because it's affecting users on mainnet right that moment, but again no waking up to
handle these issues, also, these issues will be coming in all day so it's not a just handle it in the morning thing.
1. Slack has no "ack" so when you see a message use the eyeballs.
2. 99% of the requests right not are to delete users (there is a self service path for MeWe but it's not done yet) so this will require you too wrangle some data to make sure you SHOULD delete the user.

https://github.com/ProjectLibertyLabs/custodial-wallet/blob/main/docs/new_user/Custodial%20Wallet%20New%20MeWe%20Account%20Flow%20SMS_V3.svg
https://github.com/ProjectLibertyLabs/custodial-wallet/blob/main/docs/login/Custodial%20Wallet%20Existing%20Login%20Flow-SMS_V3.svg

Here is the document converted to Markdown:

## Triage Deletion Requests.pdf

* MeWe will post users who are experiencing “Username already exist” errors in the #mewe-amplica-support Slack channel.

* Toshira is usually the person who will post. Here are the steps I perform.

1. Acknowledge you receive the request by using the :eyes emoji. This lets MeWe know we saw the request and are looking into it.

2. Check the email / phone number in our Amplica db. Using this query:

```sql
SELECT ua.id as account_id, peud.user_detail_value, peu.id, peu.provider_external_id as external_id, ukd.public_key_hex, ukd.key_usage_type, to_timestamp(peud.created_at / 1000)
FROM custodial_wallet.provider_external_user_detail as peud
INNER JOIN custodial_wallet.provider_external_user peu ON peud.provider_external_user_id = peu.id  
INNER JOIN custodial_wallet.user_key_data as ukd ON peu.user_key_data_id = ukd.id
INNER JOIN custodial_wallet.user_account as ua ON ukd.user_account_id = ua.id
WHERE peu.id IN(
    SELECT peu.id 
    FROM custodial_wallet.provider_external_user as peu
    INNER JOIN custodial_wallet.provider_external_user_detail as peud ON peu.id=peud.provider_external_user_id
    INNER JOIN custodial_wallet.user_key_data as ukd ON peu.user_key_data_id = ukd.id
    WHERE user_detail_value= ‘EMAIL/PHONE NUMBER GOES HERE’ AND ukd.key_usage_type='ACCOUNT'
)
```

3. Confirm only one public_key_hex is returned. If multiple are returned do NOT delete the user. You will need to reach out to Piotr on MeWe to coordinate what to do next. If NO public key is found, let Toshira know the user is not listed in our db under the email/phone provided.

4. Assuming only one public key is returned; Check the public key using polkadot.js tool:

   a. Developer / Chain State

   b. Select Query State: MSA and publicKeyToMsaId(AccountId32): Option<u64>

5. Paste the public key into the Account section and confirm there are no MSA’s associated with the public key. If you do find an MSA; you will need to coordinate next steps with MeWe.

6. Assuming no MSA is found, it is now safe to delete the user. Go to

   a. https://www.amplicaaccess.com/webjars/swagger-ui/index.html#/admin-controller/deleteUser

   b. Add secret key

   c. Change type to “EMAIL” OR “PHONE_NUMBER”

   d. Click Execute.

   e. Confirm 200 response.

7. Create a thread from the initial deletion request and @ Toshira or person who requested that the user has been removed.

## Dealing with support questions about email delivery
1. In [Kibana](https://logs.frequency.xyz/_plugin/kibana/app/discover#/) go to the `ses-notifications` index and just
throw the email in and see what you can see, it'll usually be very obvious what happened with a person in there
2. If the person is on the suppression list and you think it's worth removing them from the suppression list navigate to [The Suppression List](https://us-east-2.console.aws.amazon.com/ses/home?region=us-east-2#/suppression-list)
and remove them.