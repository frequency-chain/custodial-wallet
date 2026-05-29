<script lang="ts">
  interface Props {
    class?: string
  }

  let props: Props = $props()
</script>

<svg class="h-5 w-auto" viewBox="0 0 25 24" fill="none" xmlns="http://www.w3.org/2000/svg" {...props}>
  <path
    d="M2.525 24L0.125 21.6L9.725 12L0.125 2.4L2.525 0L12.125 9.6L21.725 0L24.125 2.4L14.525 12L24.125 21.6L21.725 24L12.125 14.4L2.525 24Z"
    fill="currentColor"
  />
</svg>
