export function contentSecurityPolicy(nonce: string, apiBase?: string, httpsConfirmed = false) {
  if (!/^[A-Za-z0-9+/=]{16,128}$/.test(nonce)) throw new Error('Invalid CSP nonce')
  let apiOrigin = ''
  if (apiBase) {
    const url = new URL(apiBase)
    if (!['http:', 'https:'].includes(url.protocol) || url.hostname.includes('*') || url.username || url.password || url.search || url.hash || (httpsConfirmed && url.protocol !== 'https:')) throw new Error('Invalid CSP API origin')
    apiOrigin = url.origin
  }
  return ["default-src 'self'", `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`, "script-src-attr 'none'", `style-src 'self' 'nonce-${nonce}'`, "style-src-attr 'none'", "font-src 'self'", "img-src 'self' data: blob:", `connect-src 'self'${apiOrigin ? ` ${apiOrigin}` : ''}`, "object-src 'none'", "base-uri 'none'", "frame-ancestors 'none'", "form-action 'self'", ...(httpsConfirmed ? ['upgrade-insecure-requests'] : [])].join('; ')
}
