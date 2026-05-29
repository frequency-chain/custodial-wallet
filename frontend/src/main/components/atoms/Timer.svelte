<script lang="ts">
  import type { Testable } from "$components/interfaces/Testable"

  export type Props = {
    millis: number
    startOnLoad?: boolean
    onFinished?: () => void
  } & Testable

  let { millis, startOnLoad = true, onFinished = () => {}, ...restProps }: Props = $props()

  let secondsRemaining = $state(0)
  let minutesRemaining = $state(0)

  let minuteDisplay = $derived(`${minutesRemaining}`)
  let secondDisplay = $derived(secondsRemaining < 10 ? `0${secondsRemaining}` : `${secondsRemaining}`)

  if (startOnLoad) {
    initializeTimer(millis)
  }

  function initializeTimer(millis: number) {
    let currentTime = new Date().getTime()
    let endTime = new Date(currentTime + millis)
    updateTimer()
    const timeInterval = setInterval(updateTimer, 999)

    function getTimeRemaining(endTime: Date) {
      const total = endTime.getTime() - new Date().getTime()
      const seconds = Math.floor((total / 1000) % 60)
      const minutes = Math.floor((total / 1000 / 60) % 60)
      return {
        total,
        minutes,
        seconds,
      }
    }

    function updateTimer() {
      let remaining = getTimeRemaining(endTime)
      minutesRemaining = remaining.minutes
      secondsRemaining = remaining.seconds

      if (remaining.total <= 0) {
        clearInterval(timeInterval)
        minutesRemaining = 0
        secondsRemaining = 0
        onFinished()
      }
    }
  }
</script>

<div {...restProps}>
  <span>{minuteDisplay}:{secondDisplay}</span>
</div>
