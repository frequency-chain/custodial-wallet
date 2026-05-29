# Styleguide

A collection of best practices our team strives to follow when developing Svelte / tailwind components.

## Svelte

- Always use `lang="ts"`.
- Do not use `bind:`.
- Do not use `$effect` unless absolutely necessary.

## Tailwind

- Avoid writing utility classes; Strive to use Svelte components and templating to avoid copy-pasta.
- Use semantic names as much as possible for variables in `theme.css`.
