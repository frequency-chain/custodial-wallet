import { PasskeyData } from "../helpers/interfaces"

export const acceptRegistration = async (passkeyData: PasskeyData, serverAddress: string) => {
  await $.ajax({
    url: `${serverAddress}/api/passkey/registration/accept`,
    type: "POST",
    data: JSON.stringify({
      ...passkeyData,
    }),
    contentType: "application/json; charset=utf-8",
  })
}
