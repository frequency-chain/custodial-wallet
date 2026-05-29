<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"
  import "@hcaptcha/vanilla-hcaptcha" // Defines 'h-captcha' custom element
  import type { VanillaHCaptchaWebComponent } from "@hcaptcha/vanilla-hcaptcha"
  import { fromPromise, Result } from "neverthrow"
  import { HCaptchaFailureType, hCaptchaFailureTypeFromString } from "$components/enums/HCaptchaFailureType"

  export type Props = {
    siteKey: string
  } & Testable

  let { siteKey }: Props = $props()

  let hCaptcha: VanillaHCaptchaWebComponent

  export const executeAsync = async (): Promise<Result<string, HCaptchaFailureType>> => {
    const hCaptchaResult = await fromPromise(hCaptcha.executeAsync(), (e: unknown) => {
      console.warn("hCaptcha failed:", e)
      return typeof e === "string" ? hCaptchaFailureTypeFromString(e) : HCaptchaFailureType.UNKNOWN_ERROR
    })

    return hCaptchaResult.map((response: { key: string; response: string }) => {
      console.log("hCaptcha verified:", response.response)
      return response.response
    })
  }

  const onloaded = async (event: unknown) => {
    console.log("hCaptcha loaded:", event)
  }
</script>

<h-captcha bind:this={hCaptcha} site-key={siteKey} size={"invisible"} {onloaded}></h-captcha>
