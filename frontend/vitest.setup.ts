// Minimal browser surface for service-level tests (node environment):
// js-cookie only needs a plain document.cookie store; no jsdom required.
const store: Record<string, string> = {};

Object.defineProperty(globalThis, 'document', {
  value: {},
  writable: true,
  configurable: true,
});

Object.defineProperty((globalThis as Record<string, unknown>).document, 'cookie', {
  configurable: true,
  get: () =>
    Object.entries(store)
      .map(([k, v]) => `${k}=${v}`)
      .join('; '),
  set: (raw: string) => {
    const [pair] = String(raw).split(';');
    const idx = pair.indexOf('=');
    if (idx > 0) {
      store[pair.slice(0, idx).trim()] = pair.slice(idx + 1).trim();
    }
  },
});
