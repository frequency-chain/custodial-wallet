import { hexToU8a, u8aToHex } from "@polkadot/util"
import { base64URLStringToBuffer, bufferToBase64URLString } from "@simplewebauthn/browser"

export const showSuccessMessage = (message: string) => {
  const successDiv = document.getElementById("success")
  const errorDiv = document.getElementById("error")
  if (successDiv && errorDiv) {
    if (window.getComputedStyle(errorDiv).visibility !== "hidden") errorDiv.hidden = true
    successDiv.hidden = false
    successDiv.innerText = message
  }
}

export const showErrorMessage = (message: string) => {
  const successDiv = document.getElementById("success")
  const errorDiv = document.getElementById("error")
  if (successDiv && errorDiv) {
    if (window.getComputedStyle(successDiv).visibility !== "hidden") successDiv.hidden = true
    errorDiv.hidden = false
    errorDiv.innerText = message
  }
}

export const convertHexToBase64URLString = (hexString: string) => {
  return bufferToBase64URLString(hexToU8a(hexString).buffer)
}

export const convertBase64URLStringToHex = (base64UrlString: string) => {
  return u8aToHex(new Uint8Array(base64URLStringToBuffer(base64UrlString)))
}

export const base64urlEncode = (uInt8Array: Uint8Array) => {
  // Convert the buffer to base64
  const base64 = btoa(String.fromCharCode.apply(null, uInt8Array))
  // Replace characters to make it base64url encoded
  return base64
    .replace(/\+/g, "-") // Replace + with -
    .replace(/\//g, "_") // Replace / with _
    .replace(/=+$/, "") // Remove trailing =
}

export const base64urlToUtf8 = (base64url: string) => {
  // Restore base64 padding
  let base64 = base64url.replace(/-/g, "+").replace(/_/g, "/")
  while (base64.length % 4 !== 0) {
    base64 += "="
  }
  // Use TextDecoder with base64 decoding
  const binaryString = atob(base64)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return new TextDecoder().decode(bytes)
}

export const stringToByteArray = (utf8String: string) => {
  return Array.from(new TextEncoder().encode(utf8String))
}

export const getCookie = (name: string): string | null => {
  const cookieMap = Object.fromEntries(
    document.cookie.split("; ").map((cookieKeyValuePair) => cookieKeyValuePair.split("=").map(decodeURIComponent)),
  )
  return cookieMap[name] || null
}

export const assertTypesExhausted = (x: never): never => {
  throw new Error(`Non-exhaustive handling of a union type: ${x}`)
}
