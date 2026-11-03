import process from 'node:process'
/** @type {import('next').NextConfig} */
const hsts = Number(process.env.TRAINING_PORTAL_HSTS_MAX_AGE_SECONDS || '0')
if (!Number.isInteger(hsts) || hsts < 0 || hsts > 31536000 || (hsts > 0 && process.env.TRAINING_PORTAL_HTTPS_CONFIRMED !== 'true')) throw new Error('Invalid HSTS configuration')
const nextConfig = {
  async headers() {
    return [{ source: '/:path*', headers: [
      { key: 'X-Content-Type-Options', value: 'nosniff' },
      { key: 'X-Frame-Options', value: 'DENY' },
      { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
      { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(), payment=()' },
      ...(hsts > 0 ? [{ key: 'Strict-Transport-Security', value: `max-age=${hsts}` }] : []),
    ] }]
  },
  devIndicators: false,
  images: {
    unoptimized: true,
  },
}

export default nextConfig
