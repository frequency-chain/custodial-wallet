# SIWA API Contracts
## Verified Email Json Schema
```json
{
  "name": "VerifiedEmailAddressCredentialSchema",
  "schema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "VerifiedEmailAddressCredential",
    "type": "object",
    "properties": {
      "credentialSubject": {
        "type": "object",
        "properties": {
          "emailAddress": {
            "type": "string",
            "format": "email"
          },
          "lastVerified": {
            "type": "string",
            "format": "date-time"
          }
        },
        "required": [
          "emailAddress",
          "lastVerified"
        ]
      }
    }
  }
}
```
## Verified SMS Json Schema
```json
{
  "name": "VerifiedPhoneNumberCredentialSchema",
  "schema": {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "VerifiedPhoneNumberCredential",
    "type": "object",
    "properties": {
      "credentialSubject": {
        "type": "object",
        "properties": {
          "phoneNumber": {
            "type": "string",
            "pattern": "^\\+[1-9]\\d{1,14}$"
          },
          "lastVerified": {
            "type": "string",
            "format": "date-time"
          }
        },
        "required": [
          "phoneNumber",
          "lastVerified"
        ]
      }
    }
  }
}
```
## Credential Example (faked will not verify)
```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://www.w3.org/ns/credentials/undefined-terms/v2"
  ],
  "type": [
    "VerifiedEmailAddressCredential",
    "VerifiableCredential"
  ],
  "issuer": "did:web:frequencyaccess.com",         //This references https://frequencyaccess.com/.well-known/did.json, which contains the DID document with the signing key used in "verificationMethod" below by Frequency Access
  "validFrom": "2024-02-12T03:09:40.497Z",
  "credentialSchema": {
    "type": "JsonSchema",
    "id": "https://some.permanent.url/schema/email_address.json" //Just an example now, we will put this somewhere eventually
  },
  "credentialSubject": {
    "id": "did:key:z????",     //This is the sr25519 public key of the user or the one that was just created for a new user
    "emailAddress": "joe.bloggs@myisp.net",
    "lastVerified": "2024-02-12T03:08:00Z"
  },
  "proof": {
    "type": "DataIntegrityProof",
    "created": "2024-02-12T03:09:44Z",
    "verificationMethod": "did:web:frequencyaccess.com#z6Mkumvf8FpJybzi9byLX7qAhTPuKpqH7d5rWyqcrKJ9Mies", //This will be the ED25519 public key of the custodial wallet
    "cryptosuite": "eddsa-rdfc-2022",
    "proofPurpose": "assertionMethod",
    "proofValue": "z2YLydotgaGsbRGRxPzmoscd7dH5CgGHydXLKXJXefcT2SJGExtxmkJxGfUGoe81Vm62JGEYrwcS6ht1ixEvuZF9c" //The rdfc signature using the private key of the ED25519 key
  }
}
```
## Request into SIWA

> *Copied Jan 27, 2025 from our [testnet SIWA test harness](https://github.com/ProjectLibertyLabs/custodial-wallet/blob/main/app/src/test/resources/static/testnet/siwa/request.html)*

```json
{
  "requestedSignatures": {
    "publicKey": {
      "encodedValue": "5DDCHYTEUtwtiYFbwNe8bFghg3pDESun49RLYdg9n57rbX5G",
      "encoding": "base58",
      "format": "ss58",
      "type": "Sr25519"
    },
    "signature": {
      "algo": "SR25519",
      "encoding": "base16",
      "encodedValue": "0x0af8211b36fdd44a1a8bdad4dec90749476dfb3106eeb22f5df07500db5fa9569d42da3bd372f91dd8040054096cf573bfe29b8906b4ccbe51d101d9ef1ea287"
    },
    "payload": {
      "callback": "http://localhost:52165",
      "permissions": [
        5,
        7,
        8,
        9,
        10
      ],
      "userIdentifierAdminUrl": "https://www.mewe.com"
    }
  },
  "requestedCredentials": [
    {
      "anyOf": [
        {
          "type": "VerifiedEmailAddressCredential",
          "hash": [
            "???"
          ]
        },
        {
          "type": "VerifiedPhoneNumberCredential",
          "hash": [
            "???"
          ]
        }
      ]
    }
  ]
}
```

## Response from `/siwa/api/payload`

Here is a full example of the SIWA payloads response for a user logging in via email.

> *Generated Jan 27, 2025 in testnet using our [SIWA test harness](https://github.com/ProjectLibertyLabs/custodial-wallet/blob/main/app/src/test/resources/static/testnet/siwa/request.html)*

```json
{
  "userPublicKey": {
    "encodedValue": "5DUuTDSTBoFoQH85nKHs4Wb9U3afpmA7bjRM9ZWMQ4LxHrXT",
    "encoding": "base58",
    "format": "ss58",
    "type": "Sr25519"
  },
  "userKeys": [
    {
      "encodedPublicKeyValue": "0xa9bda9221ef72c87f6aa4b5e08bd0667f12949b9ce2ea42aeea067cf6940bc1b",
      "encodedPrivateKeyValue": "0x62e83d18720754b7b7f94248c760fe8ab448b92e7a463f6828c38d98c8653e12",
      "encoding": "base16",
      "format": "bare",
      "type": "X25519",
      "keyType": "dsnp.public-key-key-agreement"
    }
  ],
  "payloads": [
    {
      "signature": {
        "algo": "SR25519",
        "encoding": "base16",
        "encodedValue": "0xa0f0ff3be048402bfe234d2011f2a9a21e0b2580cea977f3d404ab6c28e26f362b4b818c579ed8fdb2e81fb1be723f0436f52ef69485652598d1ab346e657184"
      },
      "endpoint": {
        "pallet": "msa",
        "extrinsic": "grantDelegation"
      },
      "type": "addProvider",
      "payload": {
        "authorizedMsaId": 730,
        "schemaIds": [
          5,
          7,
          8,
          9,
          10
        ],
        "expiration": 3094268
      }
    }
  ],
  "credentials": [
    {
      "@context": [
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ],
      "type": [
        "VerifiedEmailAddressCredential",
        "VerifiableCredential"
      ],
      "issuer": "did:web:testnet.frequencyaccess.com",
      "validFrom": "2025-01-27T18:09:12.633+0000",
      "credentialSchema": {
        "type": "JsonSchema",
        "id": "https://schemas.frequencyaccess.com/VerifiedEmailAddressCredential/bciqe4qoczhftici4dzfvfbel7fo4h4sr5grco3oovwyk6y4ynf44tsi.json"
      },
      "credentialSubject": {
        "id": "did:key:z6QNpE9mC36YRva7CcZB88GFrgrRQXks2hZKqwhauyKwisPs",
        "emailAddress": "julian.fortune@projectliberty.io",
        "lastVerified": "2025-01-27T18:13:34.564+0000"
      },
      "proof": {
        "type": "DataIntegrityProof",
        "verificationMethod": "did:web:testnet.frequencyaccess.com#z6Mkw4yX4c2Z3seSSdnR9svEN6Fv7UkU8jrNPMkMwtZCoAVG",
        "cryptosuite": "eddsa-rdfc-2022",
        "proofPurpose": "assertionMethod",
        "proofValue": "zJ37WpmKW41yp6Qr63vxg3SBaqzhEf2BroRCUZcD48WdEdGRJhafW71uXVtUsZMAqwGbesU9Zes8dTtJmfczrRET"
      }
    },
    {
      "@context": [
        "https://www.w3.org/ns/credentials/v2",
        "https://www.w3.org/ns/credentials/undefined-terms/v2"
      ],
      "type": [
        "VerifiedGraphKeyCredential",
        "VerifiableCredential"
      ],
      "issuer": "did:web:testnet.frequencyaccess.com",
      "validFrom": "2025-01-27T18:09:12.635+0000",
      "credentialSchema": {
        "type": "JsonSchema",
        "id": "https://schemas.frequencyaccess.com/VerifiedGraphKeyCredential/bciqmdvmxd54zve5kifycgsdtoahs5ecf4hal2ts3eexkgocyc5oca2y.json"
      },
      "credentialSubject": {
        "id": "did:key:z6QNpE9mC36YRva7CcZB88GFrgrRQXks2hZKqwhauyKwisPs",
        "encodedPublicKeyValue": "0xa9bda9221ef72c87f6aa4b5e08bd0667f12949b9ce2ea42aeea067cf6940bc1b",
        "encodedPrivateKeyValue": "0x62e83d18720754b7b7f94248c760fe8ab448b92e7a463f6828c38d98c8653e12",
        "encoding": "base16",
        "format": "bare",
        "type": "X25519",
        "keyType": "dsnp.public-key-key-agreement"
      },
      "proof": {
        "type": "DataIntegrityProof",
        "verificationMethod": "did:web:testnet.frequencyaccess.com#z6Mkw4yX4c2Z3seSSdnR9svEN6Fv7UkU8jrNPMkMwtZCoAVG",
        "cryptosuite": "eddsa-rdfc-2022",
        "proofPurpose": "assertionMethod",
        "proofValue": "z4o1DFFRrffpxciFJpYTXa15LHJmmQ1RXPWgPJrdyDmx2tm7darB91x7tECE9MXFnuQcwo254KwVGsHqNs9tZ87vx"
      }
    }
  ]
}
```

Here is a snippet showing what a phone number credential looks like; Users logging in via SMS will have this included 
in the `credentials` part of the SIWA payloads response instead of an email credential.

> *Generated Jan 28, 2025 in testnet using our [SIWA test harness](https://github.com/ProjectLibertyLabs/custodial-wallet/blob/main/app/src/test/resources/static/testnet/siwa/request.html)*

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://www.w3.org/ns/credentials/undefined-terms/v2"
  ],
  "type": [
    "VerifiedPhoneNumberCredential",
    "VerifiableCredential"
  ],
  "issuer": "did:web:testnet.frequencyaccess.com",
  "validFrom": "2025-01-28T17:20:03.400+0000",
  "credentialSchema": {
    "type": "JsonSchema",
    "id": "https://schemas.frequencyaccess.com/VerifiedPhoneNumberCredential/bciqjspnbwpc3wjx4fewcek5daysdjpbf5xjimz5wnu5uj7e3vu2uwnq.json"
  },
  "credentialSubject": {
    "id": "did:key:z6QNzxszYJfd1jzwUYaKm87A2a8MtE9exiv1zq96ZRbXpEWo",
    "phoneNumber": "+15415256986",
    "lastVerified": "2025-01-28T17:24:44.406+0000"
  },
  "proof": {
    "type": "DataIntegrityProof",
    "verificationMethod": "did:web:testnet.frequencyaccess.com#z6Mkw4yX4c2Z3seSSdnR9svEN6Fv7UkU8jrNPMkMwtZCoAVG",
    "cryptosuite": "eddsa-rdfc-2022",
    "proofPurpose": "assertionMethod",
    "proofValue": "z2v9Fgennt8f9udM9Lwc45AqKLkURWBnHvLPxNwdfwGj5iPDQcgPyenKibX9Gcvfy2NReaQxgnR95GaSv6D1m1DTo"
  }
}
```
