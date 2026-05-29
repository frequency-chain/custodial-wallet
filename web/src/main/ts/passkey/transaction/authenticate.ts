import { startAuthentication } from "@simplewebauthn/browser"
import { PublicKeyCredentialDescriptorJSON, PublicKeyCredentialRequestOptionsJSON } from "@simplewebauthn/types"
import { ApplicationError, ApplicationErrorId, ErrorType } from "../../errors"
import { performRequest } from "../helpers/api"
import { Encoding, SignatureAlgos } from "../helpers/enums"
import {
  AuthenticateCredentialData,
  CredentialResponse,
  CredentialsResponse,
  EncodedBytes,
  Signature,
} from "../helpers/interfaces"

export const authenticateCredential = async (
  origin: string,
  challenge: EncodedBytes,
  credentialsList: PublicKeyCredentialDescriptorJSON[] = [],
) => {
  try {
    const url = new URL(origin)
    const hostname = url.hostname
    //Doesn't seem like we actually use the challenge when authenticating for passkey calls, so just making it a random uuid for now
    const options: PublicKeyCredentialRequestOptionsJSON = {
      challenge: challenge.encodedValue,
      //Timeout
      timeout: 60000,
      //Relying Party Id
      rpId: hostname,
      // Allow Credentials list gives authenticator a hint towards what passkey(s) to use
      allowCredentials: credentialsList,
      //User Verification (required)
      userVerification: "required",
    }

    const credential = await handleAuthentication(options)
    console.log("Authentication successful")
    const credentialId: string = credential.id
    const clientDataJSON: EncodedBytes = {
      encoding: Encoding.BASE64URL,
      encodedValue: credential.response.clientDataJSON,
    }
    const authenticatorData: EncodedBytes = {
      encoding: Encoding.BASE64URL,
      encodedValue: credential.response.authenticatorData,
    }
    const authSignature: Signature = {
      algo: SignatureAlgos.P256,
      encoding: Encoding.BASE64URL,
      encodedValue: credential.response.signature,
    }

    const authenticateCredentialData: AuthenticateCredentialData = {
      credentialId,
      clientDataJSON,
      authenticatorData,
      signature: authSignature,
      passkeyPayloadCallHex: challenge,
    }
    return authenticateCredentialData
  } catch (e: unknown) {
    const error: ApplicationError = {
      type: ErrorType.Application,
      id: ApplicationErrorId.PasskeyAuthenticationFailed,
      description: 'Passkey authentication failed, please press "Authenticate User" button to try again',
      stacktrace: e instanceof Error ? e.stack : undefined,
    }
    throw error
  }
}

const handleAuthentication = async (options: PublicKeyCredentialRequestOptionsJSON) => {
  return await startAuthentication(options)
}

export const getCredentialResponse = async (
  credentialId: string,
  serverAddress: string,
): Promise<CredentialResponse> => {
  return performRequest(`${serverAddress}/api/passkey/credential/${credentialId}`, "GET")
}

export const getCredentialsResponse = async (serverAddress: string): Promise<CredentialsResponse> => {
  return performRequest(`${serverAddress}/api/passkey/credentials`, "GET")
}

export const createAllowCredentialsList = (
  credentialsList: CredentialsResponse,
): PublicKeyCredentialDescriptorJSON[] => {
  return credentialsList.credentials.map((credential) => {
    return {
      id: credential.credentialId,
      type: "public-key",
    }
  })
}
