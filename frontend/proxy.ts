import { NextRequest, NextResponse } from 'next/server'
import { contentSecurityPolicy } from './lib/security-headers'

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.getRandomValues(new Uint8Array(24))).toString('base64')
  const policy = contentSecurityPolicy(nonce, process.env.NEXT_PUBLIC_API_BASE_URL, process.env.TRAINING_PORTAL_HTTPS_CONFIRMED === 'true')
  const headers = new Headers(request.headers)
  // Never trust a caller-supplied nonce or CSP; Next consumes this request policy while rendering.
  headers.set('x-nonce', nonce)
  headers.set('Content-Security-Policy', policy)
  const response = NextResponse.next({ request: { headers } })
  response.headers.set(process.env.NODE_ENV === 'development' ? 'Content-Security-Policy-Report-Only' : 'Content-Security-Policy', policy)
  response.headers.set('Cache-Control', 'private, no-store')
  return response
}

export const config = { matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'] }
