const { decodeAddress } = require('@polkadot/util-crypto');
const { u8aToHex } = require('@polkadot/util');

export function getOrThrow(value: string | undefined): string {
  if(value) {
    return value;
  }else {
    throw new Error("Cannot be null");
  }
}

export interface Provider<T> {
  get(): Promise<T>;
}

export function ss58ToHex(ss58Address: string): string {
  const publicKey = decodeAddress(ss58Address);
  const hexPublicKey = u8aToHex(publicKey);
  return hexPublicKey;
}