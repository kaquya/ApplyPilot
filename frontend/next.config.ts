import type { NextConfig } from 'next';
const apiOrigin = process.env.API_INTERNAL_HOSTPORT
  ? `http://${process.env.API_INTERNAL_HOSTPORT}`
  : process.env.API_INTERNAL_URL || 'http://127.0.0.1:8080';
const config: NextConfig = {
  agentRules: false,
  devIndicators: false,
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${apiOrigin}/api/:path*`,
      },
      {
        source: '/oauth2/:path*',
        destination: `${apiOrigin}/oauth2/:path*`,
      },
      {
        source: '/login/oauth2/:path*',
        destination: `${apiOrigin}/login/oauth2/:path*`,
      },
    ];
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
        ],
      },
    ];
  },
};
export default config;
