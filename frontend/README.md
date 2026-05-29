# Frontend

A project for developing front end web components using Svelte and tailwind.

## Formatting

The source files in this module are formatted with Prettier and linted with eslint (with extra ts and storybook rules) 

The project will fail to build if either formatting or linting will fail. Run `npmFormat` to apply formatting rules.

### IntelliJ Integration

For a smoother workflow, enable Prettier on save in IntelliJ:
1. Open Settings
2. Navigate to **Languages & Frameworks > JavaScript > Prettier**
3. Check the 'Automatic' configuration option
4. Add `svelte` to the glob:
   ```**/*.{js,ts,jsx,tsx,cjs,cts,mjs,mts,vue,astro,svelte}```
5. Check the **Run on save** box

## Storybook

[Storybook](https://storybook.js.org) is a preview framework for developing components with hot-reloading.

Start Storybook in development mode with:
```
./gradlew frontend:storybook
```
or
```sh
npm run storybook
```

## Component Testing

- We use `happy-dom` because `jsdom` does not implement `HTMLDialogElement`.
