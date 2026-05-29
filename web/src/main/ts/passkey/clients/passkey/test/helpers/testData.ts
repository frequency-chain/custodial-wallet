import { Encoding, EncodingFormat, PublicKeyType, SignatureAlgos } from "@/passkey/helpers/enums"
import { EncodedBytes, PublicKey, Signature } from "@/passkey/helpers/interfaces"

export type TestPasskeyData = {
  clientDataJSON: EncodedBytes
  passkeyCompressedPublicKey: PublicKey
  credentialPublicKeySignature: Signature
  credentialId: string
  accountPublicKey: PublicKey
  credentialSignatureOfAccountPublicKey?: Signature
  attestationObject: EncodedBytes
}

export type TestAuthenticatedPasskeyData = {
  credentialId: string
  clientDataJSON: EncodedBytes
  authenticatorData: EncodedBytes
  signature: Signature
  passkeyPayloadCallHex: EncodedBytes
}

export const PASSKEY_DATA_1: TestPasskeyData = {
  clientDataJSON: {
    encodedValue:
      "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiQTNkMExiRUptMVEwODBwMnpidVlIMGthcGFwbWtmMXlVdmRQdjlwaE5nQ0EiLCJvcmlnaW4iOiJodHRwOi8vbG9jYWxob3N0OjUxODc4IiwiY3Jvc3NPcmlnaW4iOmZhbHNlfQ",
    encoding: Encoding.BASE64URL,
  },
  attestationObject: {
    encodedValue:
      "o2NmbXRmcGFja2VkZ2F0dFN0bXSjY2FsZyZjc2lnWEcwRQIhANqSBAyV5AfNrI_zp0ORS9gAqxQntcPPxPCqFHxdigEqAiAoHKql-TY5ZaZN8OrJnCVWfPYG3rVkevcn0suf1kEN12N4NWOBWQHXMIIB0zCCAXqgAwIBAgIBATAKBggqhkjOPQQDAjBgMQswCQYDVQQGEwJVUzERMA8GA1UECgwIQ2hyb21pdW0xIjAgBgNVBAsMGUF1dGhlbnRpY2F0b3IgQXR0ZXN0YXRpb24xGjAYBgNVBAMMEUJhdGNoIENlcnRpZmljYXRlMB4XDTE3MDcxNDAyNDAwMFoXDTQ1MDIyMTA2MzQzNFowYDELMAkGA1UEBhMCVVMxETAPBgNVBAoMCENocm9taXVtMSIwIAYDVQQLDBlBdXRoZW50aWNhdG9yIEF0dGVzdGF0aW9uMRowGAYDVQQDDBFCYXRjaCBDZXJ0aWZpY2F0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABI1hfmXJUI5kvMVnOsgqZ5naPBRGaCwljEY__99Y39L6Pmw3i1PXlcSk3_tBme3Xhi8jq68CA7S4kRugVpmU4QGjJTAjMAwGA1UdEwEB_wQCMAAwEwYLKwYBBAGC5RwCAQEEBAMCAwgwCgYIKoZIzj0EAwIDRwAwRAIgaW-6A4_t1F-QsNgGKoa1RXkKsSy4DUuE_r_s5pH-2rACIHItSQE2sJ4SdrUGIRav5grz9HR3JZk9Mj0JMaFDZnDXaGF1dGhEYXRhWKRJlg3liA6MaHQ0Fw9kdmBbj-SuuaKGMseZXPO6gx2XY0UAAAABAQIDBAUGBwgBAgMEBQYHCAAgWW9PKSU-lw-SAkFZiIh1cJGzQaX-2zDBYce7DZtA0G-lAQIDJiABIVgguPfeMXkqI3ZPj0smSogW7QNvQs_3wUGo4zZ6tlcj9R8iWCCHvZe9zu0nf6lPabL547vk4PwMZpEM-ouBWRr2dy_GuA",
    encoding: Encoding.BASE64URL,
  },
  credentialId: "WW9PKSU-lw-SAkFZiIh1cJGzQaX-2zDBYce7DZtA0G8",
  passkeyCompressedPublicKey: {
    encodedValue: "0x02b8f7de31792a23764f8f4b264a8816ed036f42cff7c141a8e3367ab65723f51f",
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.P256,
  },
  accountPublicKey: {
    encodedValue: "0x0377742db1099b5434f34a76cdbb981f491aa5aa6691fd7252f74fbfda61360080",
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  },
  credentialPublicKeySignature: {
    algo: SignatureAlgos.SECP256K1,
    encoding: Encoding.BASE16,
    encodedValue:
      "0x954be956775387551d47a2d4ddc61d9cbb5914c04c3a2d0af3d747c3c66f8da91648151ded629d8f98c60f30933274e338ea15505328ef1edba2cd9fbd2617e31c",
  },
  credentialSignatureOfAccountPublicKey: {
    algo: SignatureAlgos.P256,
    encoding: Encoding.BASE16,
    encodedValue:
      "0x3045022100da92040c95e407cdac8ff3a743914bd800ab1427b5c3cfc4f0aa147c5d8a012a0220281caaa5f9363965a64df0eac99c25567cf606deb5647af727d2cb9fd6410dd7",
  },
}

//This is from authenticating PASSKEY_DATA_1
export const AUTHENTICATED_PASSKEY_DATA_1: TestAuthenticatedPasskeyData = {
  credentialId: "WW9PKSU-lw-SAkFZiIh1cJGzQaX-2zDBYce7DZtA0G8",
  clientDataJSON: {
    encoding: Encoding.BASE64URL,
    encodedValue:
      "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiM0N1X2lPbGN5NDhLem4wZGtnV3h3ZXRDU25iWUhsTzJNR2FsQ0NLQ0JNbyIsIm9yaWdpbiI6Imh0dHA6Ly9sb2NhbGhvc3Q6NTE4NzgiLCJjcm9zc09yaWdpbiI6ZmFsc2UsIm90aGVyX2tleXNfY2FuX2JlX2FkZGVkX2hlcmUiOiJkbyBub3QgY29tcGFyZSBjbGllbnREYXRhSlNPTiBhZ2FpbnN0IGEgdGVtcGxhdGUuIFNlZSBodHRwczovL2dvby5nbC95YWJQZXgifQ",
  },
  authenticatorData: {
    encoding: Encoding.BASE64URL,
    encodedValue: "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAw",
  },
  signature: {
    algo: SignatureAlgos.P256,
    encoding: Encoding.BASE64URL,
    encodedValue: "MEYCIQDpOkfn1VltEZSMERtQ-FtE5VW_pA3y6hVW-V8pF84N-AIhAMFGnjnKD6eiK10PyWvYtV6fu-G2ZMqzsYFKycNSoo3t",
  },
  passkeyPayloadCallHex: {
    encoding: Encoding.BASE64URL,
    encodedValue: "hiDMuUISeZ2fGh2BcCayT3GckoPsat0JUGJGPf5UOSc",
  },
}

export const PASSKEY_DATA_2: TestPasskeyData = {
  clientDataJSON: {
    encodedValue:
      "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiQTdJYTVQZmR6OG1CLVhPUFotZzRrelpmcjJ1OUNiVzk4Q1dwZHdqS0l3SWEiLCJvcmlnaW4iOiJodHRwOi8vbG9jYWxob3N0OjU4MDA4IiwiY3Jvc3NPcmlnaW4iOmZhbHNlfQ",
    encoding: Encoding.BASE64URL,
  },
  attestationObject: {
    encodedValue:
      "o2NmbXRmcGFja2VkZ2F0dFN0bXSjY2FsZyZjc2lnWEcwRQIgTuy_8UrLrSMlUhF1Vjhi6RjTS4g4kB1zG0nhGPV7CO8CIQDMXUyZjY1L5vK2Dig73kWjFa44x_qehcW6HlEXKc4wPWN4NWOBWQHXMIIB0zCCAXqgAwIBAgIBATAKBggqhkjOPQQDAjBgMQswCQYDVQQGEwJVUzERMA8GA1UECgwIQ2hyb21pdW0xIjAgBgNVBAsMGUF1dGhlbnRpY2F0b3IgQXR0ZXN0YXRpb24xGjAYBgNVBAMMEUJhdGNoIENlcnRpZmljYXRlMB4XDTE3MDcxNDAyNDAwMFoXDTQ1MDIxOTIyMTcyMlowYDELMAkGA1UEBhMCVVMxETAPBgNVBAoMCENocm9taXVtMSIwIAYDVQQLDBlBdXRoZW50aWNhdG9yIEF0dGVzdGF0aW9uMRowGAYDVQQDDBFCYXRjaCBDZXJ0aWZpY2F0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABI1hfmXJUI5kvMVnOsgqZ5naPBRGaCwljEY__99Y39L6Pmw3i1PXlcSk3_tBme3Xhi8jq68CA7S4kRugVpmU4QGjJTAjMAwGA1UdEwEB_wQCMAAwEwYLKwYBBAGC5RwCAQEEBAMCAwgwCgYIKoZIzj0EAwIDRwAwRAIganu0nfEIvuizbJMbvuJLkIxpduTUi2ZjAGTVECu5vxcCIBvUnjVX4YLCG-a5n2LeYMk28Gus9LKLuMHQVfL0BwuIaGF1dGhEYXRhWKRJlg3liA6MaHQ0Fw9kdmBbj-SuuaKGMseZXPO6gx2XY0UAAAABAQIDBAUGBwgBAgMEBQYHCAAgUcjfFAcvCNxlmqshljY2TcD1DIZSW7NIE6g7uNukCOilAQIDJiABIVggi6IXsb3dGg5892ezwFuBrHSBgbi048r7AsIwl5e-XNwiWCA2n65-LphXP-X2sizkG8gnw7fuJjait5FF9ZX4whtwzA",
    encoding: Encoding.BASE64URL,
  },
  credentialId: "UcjfFAcvCNxlmqshljY2TcD1DIZSW7NIE6g7uNukCOg",
  passkeyCompressedPublicKey: {
    encodedValue: "0x028ba217b1bddd1a0e7cf767b3c05b81ac748181b8b4e3cafb02c2309797be5cdc",
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.P256,
  },
  accountPublicKey: {
    encodedValue: "0x03b21ae4f7ddcfc981f9738f67e83893365faf6bbd09b5bdf025a97708ca23021a",
    encoding: Encoding.BASE16,
    format: EncodingFormat.COMPRESSED_HEX,
    type: PublicKeyType.SECP256K1,
  },
  credentialPublicKeySignature: {
    algo: SignatureAlgos.SECP256K1,
    encoding: Encoding.BASE16,
    encodedValue:
      "0xc8789f5458d5664be81e53010101a3e76d3400dc4ae8b9c65fcce0a7c468cc967103c41e027d230048f43884e403593ee6b189bcbf2fa1a0264884bcc6c99e1d1b",
  },
  credentialSignatureOfAccountPublicKey: {
    algo: SignatureAlgos.P256,
    encoding: Encoding.BASE16,
    encodedValue:
      "0x304502204eecbff14acbad2325521175563862e918d34b8838901d731b49e118f57b08ef022100cc5d4c998d8d4be6f2b60e283bde45a315ae38c7fa9e85c5ba1e511729ce303d",
  },
}
