const encoder = new TextEncoder();
const decoder = new TextDecoder();
const authKeys = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const appCheckKeys = "https://firebaseappcheck.googleapis.com/v1/jwks";
const tokenUrl = "https://oauth2.googleapis.com/token";
const scopes = "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging";
const jwksCache = new Map();
let oauthCache;

export class GoogleError extends Error {
    constructor(status) {
        super("Google request failed");
        this.status = status;
    }
}

function base64url(value) {
    const bytes = typeof value === "string" ? encoder.encode(value) : new Uint8Array(value);
    return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function decodeBase64(value) {
    return Uint8Array.from(atob(value.replaceAll("-", "+").replaceAll("_", "/")), (char) => char.charCodeAt(0));
}

function decodeSegment(segment) {
    return JSON.parse(decoder.decode(decodeBase64(segment)));
}

async function json(url, options = {}) {
    const response = await fetch(url, { signal: AbortSignal.timeout(8000), ...options });
    if (!response.ok) {
        await response.body?.cancel();
        throw new GoogleError(response.status);
    }
    return response.json();
}

async function publicKeys(url) {
    const now = Date.now();
    const cached = jwksCache.get(url);
    if (cached?.expires > now) return cached.keys;
    const response = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new GoogleError(response.status);
    const { keys } = await response.json();
    const maxAge = Number(response.headers.get("cache-control")?.match(/max-age=(\d+)/)?.[1] ?? 300);
    const imported = new Map(await Promise.all(keys.filter((key) => key.kty === "RSA").map(async (key) => [
        key.kid,
        await crypto.subtle.importKey("jwk", key, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]),
    ])));
    jwksCache.set(url, { keys: imported, expires: now + Math.max(30, Math.min(maxAge, 21600)) * 1000 });
    return imported;
}

async function verify(token, keysUrl) {
    if (typeof token !== "string" || token.length > 8192) return null;
    const segments = token.split(".");
    if (segments.length !== 3) return null;
    let header, claims, signature;
    try {
        header = decodeSegment(segments[0]);
        claims = decodeSegment(segments[1]);
        signature = decodeBase64(segments[2]);
    } catch {
        return null;
    }
    if (header?.alg !== "RS256" || typeof header.kid !== "string" || !claims) return null;
    const key = (await publicKeys(keysUrl)).get(header.kid);
    if (!key || !await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, encoder.encode(segments.slice(0, 2).join(".")))) {
        return null;
    }
    const now = Math.floor(Date.now() / 1000);
    if (!Number.isInteger(claims.exp) || claims.exp <= now || !Number.isInteger(claims.iat) || claims.iat > now + 30) {
        return null;
    }
    return { header, claims, now };
}

export async function verifyAppAndAccount(request, env) {
    const app = await verify(request.headers.get("X-Firebase-AppCheck"), appCheckKeys);
    if (app?.header.typ !== "JWT" || app.claims.iss !== `https://firebaseappcheck.googleapis.com/${env.PROJECT_NUMBER}`
        || !Array.isArray(app.claims.aud) || !app.claims.aud.includes(`projects/${env.PROJECT_NUMBER}`)
        || app.claims.sub !== env.ANDROID_APP_ID) return null;

    const auth = await verify(request.headers.get("Authorization")?.match(/^Bearer (\S+)$/)?.[1], authKeys);
    if (auth?.claims.iss !== `https://securetoken.google.com/${env.PROJECT_ID}`
        || auth.claims.aud !== env.PROJECT_ID || !Number.isInteger(auth.claims.auth_time)
        || auth.claims.auth_time > auth.now + 30) return null;
    return auth.claims.sub;
}

async function appAccessToken(env) {
    const account = JSON.parse(env.GROUP_PUSH_SERVICE_ACCOUNT);
    const now = Math.floor(Date.now() / 1000);
    if (oauthCache?.keyId === account.private_key_id && oauthCache.expires > now + 60) return oauthCache.token;
    const der = account.private_key.replace(/-----[^-]+-----|\s/g, "");
    const signingKey = await crypto.subtle.importKey(
        "pkcs8", decodeBase64(der), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
    );
    const jwt = [
        base64url(JSON.stringify({ alg: "RS256", typ: "JWT", kid: account.private_key_id })),
        base64url(JSON.stringify({ iss: account.client_email, scope: scopes, aud: tokenUrl, iat: now, exp: now + 3600 })),
    ].join(".");
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signingKey, encoder.encode(jwt));
    const result = await json(tokenUrl, {
        method: "POST",
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${jwt}.${base64url(signature)}` }),
    });
    if (typeof result.access_token !== "string" || !Number.isFinite(result.expires_in)) throw new Error("Missing access token");
    oauthCache = { keyId: account.private_key_id, token: result.access_token, expires: now + result.expires_in };
    return oauthCache.token;
}

function fromValue(value) {
    if ("stringValue" in value) return value.stringValue;
    if ("integerValue" in value) return Number(value.integerValue);
    if ("booleanValue" in value) return value.booleanValue;
    if ("timestampValue" in value) return Date.parse(value.timestampValue);
    if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(fromValue);
    return null;
}

function fields(document) {
    return Object.fromEntries(Object.entries(document?.fields ?? {}).map(([key, value]) => [key, fromValue(value)]));
}

export async function googleGroupClient(env) {
    const token = await appAccessToken(env);
    const documentRoot = `projects/${env.PROJECT_ID}/databases/(default)/documents`;
    const db = `https://firestore.googleapis.com/v1/${documentRoot}`;
    const messaging = `https://fcm.googleapis.com/v1/projects/${env.PROJECT_ID}/messages:send`;
    const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
    return {
        async getAll(paths) {
            if (!paths.length) return [];
            const entries = await json(`${db}:batchGet`, {
                method: "POST", headers, body: JSON.stringify({ documents: paths.map((path) => `${documentRoot}/${path}`) }),
            });
            const documents = new Map(entries.filter((entry) => entry.found).map(({ found }) => [
                found.name.slice(documentRoot.length + 1), { data: fields(found), version: found.updateTime },
            ]));
            return paths.map((path) => documents.get(path) ?? null);
        },
        async updateDelivery(path, values, version) {
            const condition = version ? { "currentDocument.updateTime": version } : { "currentDocument.exists": "false" };
            const data = Object.fromEntries(Object.entries(values).map(([key, value]) => [
                key, typeof value === "number" ? { integerValue: String(value) } : { stringValue: value },
            ]));
            const updated = await json(`${db}/${path}?${new URLSearchParams(condition)}`, {
                method: "PATCH", headers, body: JSON.stringify({ fields: data }),
            });
            return updated.updateTime;
        },
        async push(token, data) {
            const response = await fetch(messaging, {
                method: "POST", headers, signal: AbortSignal.timeout(5000),
                body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl: "600s" } } }),
            });
            await response.body?.cancel();
            if (!response.ok && response.status !== 400 && response.status !== 404) {
                throw new GoogleError(response.status);
            }
        },
    };
}
