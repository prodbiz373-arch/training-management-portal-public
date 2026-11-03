import { describe, expect, it } from 'vitest'
import { contentSecurityPolicy } from '@/lib/security-headers'
import { proxy } from '../proxy'
import { NextRequest } from 'next/server'
const nonce = 'a'.repeat(32)
describe('CSP', () => {
  it('uses nonce and restrictive directives without unsafe allowances', () => {
    const csp = contentSecurityPolicy(nonce, 'https://api.example.invalid/v1')
    expect(csp).toContain(`'nonce-${nonce}'`)
    expect(csp).toContain("frame-ancestors 'none'")
    expect(csp).toContain("style-src-attr 'none'")
    expect(csp).toContain("connect-src 'self' https://api.example.invalid")
    expect(csp).not.toContain('unsafe-inline'); expect(csp).not.toContain('unsafe-eval')
    expect(csp).not.toContain('upgrade-insecure-requests')
  })
  it('only upgrades HTTP when HTTPS deployment is explicitly confirmed', () => { expect(contentSecurityPolicy(nonce, 'https://api.example.invalid', true)).toContain('upgrade-insecure-requests') })
  it.each(['https://*.invalid', 'javascript:alert(1)', 'https://user:secret@example.invalid', 'https://example.invalid/?key=private', 'https://example.invalid/#private'])('rejects unsafe API configuration: %s', value => { expect(() => contentSecurityPolicy(nonce, value)).toThrow() })
  it('rejects header/nonce injection and mixed content under HTTPS configuration', () => { expect(() => contentSecurityPolicy("x'; script-src *")).toThrow(); expect(() => contentSecurityPolicy(nonce, 'http://api.example.invalid', true)).toThrow() })
  it('overwrites caller-controlled headers and generates a fresh nonce per response', () => {
    const request = () => new NextRequest('http://localhost/home', { headers: { 'x-nonce': 'attacker', 'Content-Security-Policy': 'script-src *' } })
    const first = proxy(request()), second = proxy(request())
    const csp = first.headers.get('Content-Security-Policy')!
    expect(csp).toBeTruthy(); expect(csp).not.toContain('attacker'); expect(csp).not.toBe(second.headers.get('Content-Security-Policy'))
    expect(first.headers.get('Cache-Control')).toBe('private, no-store')
  })
})
