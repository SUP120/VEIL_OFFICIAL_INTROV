/**
 * VEIL — LiveKit Token Server (Cloudflare Worker)
 * ─────────────────────────────────────────────────────────────────────────────
 * FREE — no credit card, 100,000 requests/day on Cloudflare's free plan.
 *
 * SETUP (one-time, takes ~5 minutes):
 * 1. Go to https://cloudflare.com → sign up free (no CC required)
 * 2. Dashboard → Workers & Pages → Create Application → Create Worker
 * 3. Replace the default code with this entire file → Deploy
 * 4. Go to the worker's Settings → Variables → add these Environment Variables:
 *      LIVEKIT_API_KEY    = APINn4KgQyr5hXj
 *      LIVEKIT_API_SECRET = cXzrjPhSCevPeaJWuqROtTPwZjqeoQn98ofgAftDW2hC
 *      LIVEKIT_URL        = wss://veil-ay98c0vf.livekit.cloud
 * 5. Copy the Worker URL (e.g. https://veil-voice-token.YOUR_NAME.workers.dev)
 * 6. Add to local.properties:  livekit.token.url=https://veil-voice-token.YOUR_NAME.workers.dev
 * ─────────────────────────────────────────────────────────────────────────────
 */

const CORS = {
  'Access-Control-Allow-Origin':  '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

export default {
  async fetch(request, env) {

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS });
    }

    if (request.method !== 'POST') {
      return resp({ error: 'POST only' }, 405);
    }

    // Parse body
    let body;
    try { body = await request.json(); }
    catch { return resp({ error: 'Invalid JSON body' }, 400); }

    const { uid, roomName, canPublish = true } = body;

    if (!uid || !roomName) {
      return resp({ error: 'uid and roomName are required' }, 400);
    }

    // Validate env vars
    const apiKey    = env.LIVEKIT_API_KEY;
    const apiSecret = env.LIVEKIT_API_SECRET;
    const lkUrl     = env.LIVEKIT_URL;

    if (!apiKey || !apiSecret || !lkUrl) {
      return resp({
        error: 'Worker env vars not configured — add LIVEKIT_API_KEY, LIVEKIT_API_SECRET, LIVEKIT_URL in Cloudflare dashboard'
      }, 500);
    }

    // Generate token
    try {
      const token = await makeLiveKitToken(apiKey, apiSecret, uid, roomName, canPublish);
      return resp({ token, livekitUrl: lkUrl });
    } catch (e) {
      return resp({ error: `Token generation failed: ${e.message}` }, 500);
    }
  },
};

// ─────────────────────────────────────────────────────────────────────────────
//  LiveKit JWT using Web Crypto API — zero npm dependencies, works everywhere
// ─────────────────────────────────────────────────────────────────────────────

async function makeLiveKitToken(apiKey, apiSecret, identity, room, canPublish) {
  const now = Math.floor(Date.now() / 1000);

  const header = { alg: 'HS256', typ: 'JWT' };

  const payload = {
    exp: now + 3600,       // valid 1 hour
    nbf: now - 5,          // allow minor clock skew
    iss: apiKey,
    sub: identity,
    jti: `${identity}-${now}`,
    video: {
      roomJoin:       true,
      room:           room,
      canPublish:     Boolean(canPublish),
      canSubscribe:   true,
      canPublishData: true,
    },
  };

  const encHeader  = b64url(JSON.stringify(header));
  const encPayload = b64url(JSON.stringify(payload));
  const sigInput   = `${encHeader}.${encPayload}`;

  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(apiSecret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );

  const sigBuf = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(sigInput));

  return `${sigInput}.${b64urlBuf(sigBuf)}`;
}

// Base64url encode a plain string (UTF-8 safe)
function b64url(str) {
  return b64urlBuf(new TextEncoder().encode(str));
}

// Base64url encode an ArrayBuffer / Uint8Array
function b64urlBuf(buf) {
  let bin = '';
  const bytes = new Uint8Array(buf instanceof ArrayBuffer ? buf : buf.buffer ?? buf);
  for (let i = 0; i < bytes.byteLength; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

// JSON response helper
function resp(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });
}
