export const getSessionId = (): string => {
  return Object.fromEntries(document.cookie.split("; ").map((c) => c.split("=")))["SESSION_ID"]
}
