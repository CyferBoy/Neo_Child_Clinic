// `vite` is only present nested under `vitest`, so `vite/client` (which declares `*?raw`)
// is not resolvable from here. Declare just the bit test/setup.ts imports.
declare module "*?raw" {
  const content: string;
  export default content;
}
