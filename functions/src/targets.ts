/**
 * Upstream registry for the API relay.
 *
 * Every upstream the app talks to is declared here. Anything not declared —
 * or any path that does not match `allow` — is rejected before a byte leaves
 * the function. This is what stops the relay from becoming an open proxy that
 * anyone with a Firebase account can point at arbitrary hosts.
 */
import { createHash, createHmac } from "crypto";
import { defineSecret } from "firebase-functions/params";

export const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
export const FASHN_API_KEY = defineSecret("FASHN_API_KEY");
export const SERP_API_KEY = defineSecret("SERP_API_KEY");
export const SCRAPER_API_KEY = defineSecret("SCRAPER_API_KEY");
export const RAPID_API_KEY = defineSecret("RAPID_API_KEY");
export const GOOGLE_SEARCH_KEY = defineSecret("GOOGLE_SEARCH_KEY");
export const GOOGLE_SEARCH_CX = defineSecret("GOOGLE_SEARCH_CX");
export const UNSPLASH_ACCESS_KEY = defineSecret("UNSPLASH_ACCESS_KEY");
export const EBAY_CLIENT_ID = defineSecret("EBAY_CLIENT_ID");
export const EBAY_CLIENT_SECRET = defineSecret("EBAY_CLIENT_SECRET");

// Taobao Union (Alimama). The secret signs every request and must never reach
// a client — which is the whole reason this target exists here rather than in
// the app. TAOBAO_ADZONE_ID identifies the ad slot commission is paid against.
export const TAOBAO_APP_KEY = defineSecret("TAOBAO_APP_KEY");
export const TAOBAO_APP_SECRET = defineSecret("TAOBAO_APP_SECRET");
export const TAOBAO_ADZONE_ID = defineSecret("TAOBAO_ADZONE_ID");

// AliExpress affiliate. TRACKING_ID is the affiliate slot commission is paid
// against — and the API returns an empty result rather than an error when it is
// missing, so it is injected here and never left to the caller.
export const ALIEXPRESS_APP_KEY = defineSecret("ALIEXPRESS_APP_KEY");
export const ALIEXPRESS_APP_SECRET = defineSecret("ALIEXPRESS_APP_SECRET");
export const ALIEXPRESS_TRACKING_ID = defineSecret("ALIEXPRESS_TRACKING_ID");
/** "sha256" (current gateway) or "md5" (older). Set once the real key confirms it. */
export const ALIEXPRESS_SIGN_METHOD = defineSecret("ALIEXPRESS_SIGN_METHOD");

export const ALL_SECRETS = [
  OPENAI_API_KEY,
  FASHN_API_KEY,
  SERP_API_KEY,
  SCRAPER_API_KEY,
  RAPID_API_KEY,
  GOOGLE_SEARCH_KEY,
  GOOGLE_SEARCH_CX,
  UNSPLASH_ACCESS_KEY,
  EBAY_CLIENT_ID,
  EBAY_CLIENT_SECRET,
  TAOBAO_APP_KEY,
  TAOBAO_APP_SECRET,
  TAOBAO_ADZONE_ID,
  ALIEXPRESS_APP_KEY,
  ALIEXPRESS_APP_SECRET,
  ALIEXPRESS_TRACKING_ID,
  ALIEXPRESS_SIGN_METHOD,
];

/** Quota units charged per call. Roughly proportional to what the call costs us. */
export type Weigher = (path: string) => number;

export interface Target {
  /** Fixed upstream origin, or null when the client picks one from `hostAllow`. */
  origin: string | null;
  /** For dynamic-origin targets (RapidAPI): exact hostnames we will forward to. */
  hostAllow?: string[];
  /** Path allowlist. A request must match one of these. */
  allow: RegExp[];
  /** Attach upstream credentials. Mutates `headers`/`url` in place. */
  authorize: (headers: Headers, url: URL) => void | Promise<void>;
  /** Quota cost of a call. */
  weigh: Weigher;
}

const flat = (n: number): Weigher => () => n;

export const TARGETS: Record<string, Target> = {
  openai: {
    origin: "https://api.openai.com",
    allow: [
      /^\/v1\/chat\/completions$/,
      /^\/v1\/images\/(edits|generations)$/,
    ],
    authorize: (h) => h.set("authorization", `Bearer ${OPENAI_API_KEY.value()}`),
    // An image edit at 1024x1536 costs ~100x a small vision chat call. Price it that way.
    weigh: (p) => (p.startsWith("/v1/images/") ? 25 : 1),
  },

  fashn: {
    origin: "https://api.fashn.ai",
    allow: [/^\/v1\/run$/, /^\/v1\/status\/[A-Za-z0-9_-]+$/],
    authorize: (h) => h.set("authorization", `Bearer ${FASHN_API_KEY.value()}`),
    // Polling /status is cheap; only charge for the run that starts a job.
    weigh: (p) => (p === "/v1/run" ? 20 : 0),
  },

  serpapi: {
    origin: "https://serpapi.com",
    allow: [/^\/search(\.json)?$/],
    authorize: (_h, u) => u.searchParams.set("api_key", SERP_API_KEY.value()),
    weigh: flat(2),
  },

  scraperapi: {
    origin: "https://api.scraperapi.com",
    allow: [/^\/structured\/amazon\/search$/],
    authorize: (_h, u) => u.searchParams.set("api_key", SCRAPER_API_KEY.value()),
    weigh: flat(2),
  },

  rapidapi: {
    origin: null,
    hostAllow: [
      "taobao-datahub.p.rapidapi.com",
      "shein-scraper-api.p.rapidapi.com",
      "vinted3.p.rapidapi.com",
      "aliexpress-datahub.p.rapidapi.com",
      "asos2.p.rapidapi.com",
      "theiconic.p.rapidapi.com",
    ],
    allow: [/^\/[A-Za-z0-9/_-]*$/],
    authorize: (h, u) => {
      h.set("x-rapidapi-key", RAPID_API_KEY.value());
      h.set("x-rapidapi-host", u.hostname);
    },
    weigh: flat(2),
  },

  googlesearch: {
    origin: "https://www.googleapis.com",
    allow: [/^\/customsearch\/v1$/],
    authorize: (_h, u) => {
      u.searchParams.set("key", GOOGLE_SEARCH_KEY.value());
      u.searchParams.set("cx", GOOGLE_SEARCH_CX.value());
    },
    weigh: flat(1),
  },

  unsplash: {
    origin: "https://api.unsplash.com",
    allow: [/^\/search\/photos$/],
    authorize: (_h, u) =>
      u.searchParams.set("client_id", UNSPLASH_ACCESS_KEY.value()),
    weigh: flat(1),
  },

  /**
   * Taobao Union — the official affiliate API, and the only supported way to
   * read Taobao product data. Requests carry an MD5 signature computed over
   * every parameter with the app secret on both ends, so the signing has to
   * happen after we add app_key and timestamp, and can only happen here.
   */
  taobaounion: {
    origin: "https://eco.taobao.com",
    allow: [/^\/router\/rest$/],
    authorize: (_h, u) => {
      const p = u.searchParams;
      p.set("app_key", TAOBAO_APP_KEY.value());
      p.set("format", "json");
      p.set("v", "2.0");
      p.set("sign_method", "md5");
      p.set("timestamp", beijingTimestamp());
      // The ad slot is ours, not the caller's: it is where commission lands.
      if (p.has("adzone_id")) p.set("adzone_id", TAOBAO_ADZONE_ID.value());
      p.delete("sign");
      p.set("sign", topSign(p, TAOBAO_APP_SECRET.value()));
    },
    weigh: flat(2),
  },

  /**
   * AliExpress affiliate. Same family of protocol as Taobao but a different
   * gateway, and the signature may be HMAC-SHA256 or the older MD5 wrap
   * depending on the app — the live endpoint validates the app key before the
   * signature, so which one applies cannot be determined without a real key.
   * Both are implemented; ALIEXPRESS_SIGN_METHOD selects.
   */
  aliexpress: {
    origin: "https://api-sg.aliexpress.com",
    allow: [/^\/sync$/],
    authorize: (_h, u) => {
      const p = u.searchParams;
      const method = (ALIEXPRESS_SIGN_METHOD.value() || "sha256").toLowerCase();
      p.set("app_key", ALIEXPRESS_APP_KEY.value());
      p.set("format", "json");
      p.set("v", "2.0");
      p.set("sign_method", method);
      p.set("timestamp", method === "md5" ? beijingTimestamp() : String(Date.now()));
      // Ours, not the caller's: this is where commission lands, and omitting it
      // returns an empty product list with no error at all.
      p.set("tracking_id", ALIEXPRESS_TRACKING_ID.value());
      p.delete("sign");
      p.set(
        "sign",
        method === "md5"
          ? topSign(p, ALIEXPRESS_APP_SECRET.value())
          : hmacSign(p, ALIEXPRESS_APP_SECRET.value())
      );
    },
    weigh: flat(2),
  },

  ebay: {
    origin: "https://api.ebay.com",
    // The client never sees an eBay token: the OAuth exchange happens here and
    // /identity/** is deliberately NOT in the allowlist.
    allow: [/^\/buy\/browse\/v1\/.+$/],
    authorize: async (h) => h.set("authorization", `Bearer ${await ebayToken()}`),
    weigh: flat(1),
  },
};

// --- Taobao Open Platform (TOP) request signing ---

/** TOP wants Beijing wall-clock time, not UTC, in `yyyy-MM-dd HH:mm:ss`. */
export function beijingTimestamp(now: number = Date.now()): string {
  const beijing = new Date(now + 8 * 60 * 60 * 1000);
  return beijing.toISOString().replace("T", " ").slice(0, 19);
}

/**
 * MD5 over secret + every key and value sorted by key + secret, uppercased.
 * Any deviation — a stray parameter, the wrong sort, the wrong clock — comes
 * back as an opaque "invalid signature" from Taobao.
 */
export function topSign(params: URLSearchParams, secret: string): string {
  const keys = [...params.keys()].sort();
  const joined = keys.map((k) => k + params.get(k)).join("");
  return createHash("md5")
    .update(secret + joined + secret, "utf8")
    .digest("hex")
    .toUpperCase();
}

/**
 * HMAC-SHA256 over the sorted key/value concatenation. Unlike the MD5 form the
 * secret is the HMAC key rather than padding wrapped around the payload.
 */
export function hmacSign(params: URLSearchParams, secret: string): string {
  const keys = [...params.keys()].sort();
  const joined = keys.map((k) => k + params.get(k)).join("");
  return createHmac("sha256", secret).update(joined, "utf8").digest("hex").toUpperCase();
}

// --- eBay application token, minted here and cached for the instance lifetime ---

let ebayCache: { token: string; expiresAt: number } | null = null;

async function ebayToken(): Promise<string> {
  if (ebayCache && Date.now() < ebayCache.expiresAt) return ebayCache.token;

  const basic = Buffer.from(
    `${EBAY_CLIENT_ID.value()}:${EBAY_CLIENT_SECRET.value()}`
  ).toString("base64");

  const res = await fetch("https://api.ebay.com/identity/v1/oauth2/token", {
    method: "POST",
    headers: {
      authorization: `Basic ${basic}`,
      "content-type": "application/x-www-form-urlencoded",
    },
    body: "grant_type=client_credentials&scope=https%3A%2F%2Fapi.ebay.com%2Foauth%2Fapi_scope",
  });

  if (!res.ok) {
    throw new Error(`eBay token exchange failed: ${res.status} ${await res.text()}`);
  }

  const json = (await res.json()) as { access_token: string; expires_in: number };
  ebayCache = {
    token: json.access_token,
    // Refresh a minute early so an in-flight call never races the expiry.
    expiresAt: Date.now() + (json.expires_in - 60) * 1000,
  };
  return ebayCache.token;
}
