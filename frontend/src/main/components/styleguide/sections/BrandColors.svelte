<script lang="ts">
</script>

<div class="max-w-200 font-bold [&>h5]:mt-6 [&>h5]:mb-4">
  <h5 class="border-c-border-default border-b-2">Brand Colors</h5>
  <div class="flex flex-col gap-y-4">
    <div
      class="border-c-border-default rounded-input-default text-c-text-negative flex h-48 w-full overflow-clip *:content-end *:p-4"
    >
      <div class="bg-c-sb-primary flex-grow-1">
        <p>Color - Primary</p>
      </div>
      <div class="bg-c-sb-primary-25">
        <p>Purple 25</p>
      </div>
      <div class="bg-c-sb-primary-50 *:text-c-text-default">
        <p>Purple 50</p>
      </div>
      <div class="bg-c-sb-primary-75 *:text-c-text-default">
        <p>Purple 75</p>
      </div>
    </div>
    <div
      class="*:rounded-input-default *:text-c-text-negative flex flex-row gap-3 *:h-20 *:w-full *:content-center *:px-2"
    >
      <div class="bg-c-sb-primary-dark">
        <p>Color - Dark Purple</p>
      </div>
      <div class="bg-c-sb-secondary">
        <p>Color - Secondary</p>
      </div>
    </div>
  </div>

  <h5 class="border-c-border-default border-b-2">State Colors</h5>
  <div
    class="*:rounded-input-default *:text-c-text-negative flex flex-row gap-3 *:h-15 *:w-full *:content-center *:px-2"
  >
    <div class="bg-c-sb-error">
      <p>Color - Error</p>
    </div>
    <div class="bg-c-sb-warning">
      <p>Color - Warning</p>
    </div>
    <div class="bg-c-sb-info">
      <p>Color - Info</p>
    </div>
    <div class="bg-c-sb-success">
      <p>Color - Success</p>
    </div>
  </div>

  <div
    class="*:rounded-input-default *:text-c-text-negative mt-4 flex flex-row gap-3 *:h-15 *:w-full *:content-center *:px-2"
  >
    <div class="bg-c-sb-error-focus">
      <p>Color - Error (Focused)</p>
    </div>
    <div></div>
    <div></div>
    <div></div>
  </div>

  <h5 class="border-c-border-default border-b-2">Neutral Colors</h5>
  <div
    class="*:rounded-input-default *:border-c-border-default *:text-c-text-negative flex flex-row gap-3 pt-3 pb-10 *:h-15 *:w-full *:content-center *:border *:px-2"
  >
    <div class="bg-c-sb-black">
      <p>Black</p>
    </div>
    <div class="bg-c-sb-gray-1">
      <p>Gray 1</p>
    </div>
    <div class="bg-c-sb-gray-2">
      <p>Gray 2</p>
    </div>
    <div class="bg-c-sb-gray-3">
      <p class="text-c-text-default">Gray 3</p>
    </div>
    <div class="bg-c-sb-white">
      <p class="text-c-text-default">White</p>
    </div>
    <div class="bg-c-sb-cream">
      <p class="text-c-text-default">Cream</p>
    </div>
  </div>

  <h5 class="border-c-border-default border-b-2">Gradients</h5>
  <div
    class="*:rounded-input-default *:border-c-border-default *:text-c-text-negative flex flex-col gap-3 pt-3 pb-10 *:h-28 *:w-full *:content-end *:border *:p-2"
  >
    <div class="bg-linear-(--gradient-primary)">
      <p>Primary</p>
    </div>
    <div class="bg-linear-(--gradient-secondary)">
      <p>Secondary</p>
    </div>
  </div>
</div>
