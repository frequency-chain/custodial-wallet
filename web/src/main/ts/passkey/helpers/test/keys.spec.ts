import { hexToU8a, u8aToHex } from "@polkadot/util"
import { strict as assertions } from "node:assert/strict"
import { describe, it } from "vitest"
import { getUnifiedAccountAddress } from "../keys"

describe("keys", () => {
  describe("getUnifiedPublicKey", () => {
    it("correctly builds the suffixed key", async () => {
      const input = "0x19a701d23f0ee1748b5d5f883cb833943096c6c4"
      const expected = "0x19a701d23f0ee1748b5d5f883cb833943096c6c4eeeeeeeeeeeeeeeeeeeeeeee"
      const inputU8a = hexToU8a(input)
      const output = getUnifiedAccountAddress(inputU8a)
      assertions.equal(expected, u8aToHex(output))
    })
  })
})
