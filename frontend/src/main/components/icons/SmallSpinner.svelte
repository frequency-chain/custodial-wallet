<script lang="ts">
  import { cn } from "$lib/utils/util"

  interface Props {
    class?: string
  }

  let { class: additionalClasses }: Props = $props()
</script>

<svg
  class={cn("animate-spin", additionalClasses)}
  width="22"
  height="22"
  viewBox="0 0 81 80"
  fill="none"
  xmlns="http://www.w3.org/2000/svg"
>
  <g clip-path="url(#paint0_angular_3967_806_clip_path)">
    <g transform="matrix(0 0.039746 -0.039746 0 40.4999 39.746)">
      <foreignObject x="-1020" y="-1020" width="2040" height="2040">
        <div
          style="background:conic-gradient(from 90deg,rgba(256, 256, 256, 0) 0deg,rgba(198, 198, 198, 1) 180deg,rgba(139, 139, 139, 1) 360deg);height:100%;width:100%;opacity:1"
        ></div>
      </foreignObject>
    </g>
  </g>
  <path
    d="M80.246 39.746C80.246 61.6972 62.4511 79.4921 40.4999 79.4921C18.5488 79.4921 0.753906 61.6972 0.753906 39.746C0.753906 17.7949 18.5488 0 40.4999 0C62.4511 0 80.246 17.7949 80.246 39.746ZM12.639 39.746C12.639 55.1332 25.1128 67.607 40.4999 67.607C55.8871 67.607 68.3609 55.1332 68.3609 39.746C68.3609 24.3589 55.8871 11.8851 40.4999 11.8851C25.1128 11.8851 12.639 24.3589 12.639 39.746Z"
  />
  <circle cx="40.8975" cy="73.5303" r="5.96191" fill="#8b8b8b" />
  <defs>
    <clipPath id="paint0_angular_3967_806_clip_path"
      ><path
        d="M80.246 39.746C80.246 61.6972 62.4511 79.4921 40.4999 79.4921C18.5488 79.4921 0.753906 61.6972 0.753906 39.746C0.753906 17.7949 18.5488 0 40.4999 0C62.4511 0 80.246 17.7949 80.246 39.746ZM12.639 39.746C12.639 55.1332 25.1128 67.607 40.4999 67.607C55.8871 67.607 68.3609 55.1332 68.3609 39.746C68.3609 24.3589 55.8871 11.8851 40.4999 11.8851C25.1128 11.8851 12.639 24.3589 12.639 39.746Z"
      /></clipPath
    ></defs
  >
</svg>
