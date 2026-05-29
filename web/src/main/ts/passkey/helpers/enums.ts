export enum PasskeyPublicKeyAlgos {
  ES256 = -7,
  EDDSA = -8,
  RS256 = -257,
}

export enum Encoding {
  BASE16 = "base16",
  BASE64URL = "base64urlencoded",
}

export enum SignatureAlgos {
  SR25519 = "SR25519",
  P256 = "P256_COMPRESSED",
  SECP256K1 = "SECP256K1",
}

export enum EncodingFormat {
  BARE = "bare",
  COMPRESSED_HEX = "compressedHex",
}

export enum PublicKeyType {
  P256 = "P256_COMPRESSED",
  SR25519 = "Sr25519",
  SECP256K1 = "SECP256K1",
}

export enum AccountType {
  SR25519 = "SR25519",
  ETHEREUM = "ETHEREUM",
  ETHEREUM_EIP_712 = "ETHEREUM_EIP_712",
}

export enum ChainType {
  POLKADOT = "POLKADOT",
  ETHEREUM = "ETHEREUM",
}
