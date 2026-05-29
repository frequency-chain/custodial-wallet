import { Keyring } from "@polkadot/api"
import { connect } from "./chain"
import { getMsa } from "./getMsa"
import { getElementByIdOrThrow } from "./passkey/helpers/document"

export async function printAliceDetails() {
  const api = await connect("ws://localhost:9944")
  const keyring = new Keyring({ type: "sr25519", ss58Format: 2 })
  const aliceKeypair = keyring.addFromUri("//Alice")
  console.log("Public Key: ", aliceKeypair.publicKey.toString())
  getElementByIdOrThrow("keypair").innerText = aliceKeypair.publicKey.toString()

  getMsa(api, aliceKeypair).then((value) => {
    console.log("Msa: ", value.result)
    getElementByIdOrThrow("msa").innerText = value.result?.toString() || ""
  })
}
