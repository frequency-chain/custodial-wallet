const toByteSize = (str: string) => new Blob([str]).size

// Check handle against 'Pre-Normalization Validation'
// - MUST be UTF-8
// - MUST NOT be more than 26 bytes
// - MUST not contain one of the blocked characters: " # % ( ) , . / : ; < > @ \ ` { }
//
// See: https://github.com/frequency-chain/frequency/blob/5803afafe097ec5772e08f85a3f44412491f2812/pallets/handles/README.md#pre-normalization-validation
export const isValidPreNormalization = (handle: string) => {
  return toByteSize(handle) <= 26 && /^[^"#%(),.\/:;<>@\\`{}]{3,}$/.test(handle)
}
