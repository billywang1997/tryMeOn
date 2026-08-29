/**
 * Authenticated API relay.
 *
 * The Android app used to hold every upstream key in BuildConfig, which meant
 * unzipping the APK handed anyone our OpenAI, FASHN, SerpAPI and eBay
 * credentials. Keys now live only in Secret Manager and the app calls this
 * function instead, identifying itself with a Firebase ID token.
 *
 * Request shape:
 *   POST/GET https://<region>-<project>.cloudfunctions.net/relay/<upstream path>
 *   Authorization: Bearer <Firebase ID token>
 *   X-Relay-Target: openai | fashn | serpapi | scraperapi | rapidapi |
 *                   googlesearch | unsplash | ebay
 *   X-Relay-Host:   <hostname>   (rapidapi only)
 */
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { TARGETS, ALL_SECRETS } from "./targets";
import { charge, refund, QuotaExceeded } from "./quota";

export { verifyPurchase } from "./purchases";

initializeApp();

/** Headers that describe *our* hop and must not be replayed upstream. */
const STRIP = new Set([
  "host",
  "authorization",
  "content-length",
  "connection",
  "keep-alive",
  "transfer-encoding",
  "upgrade",
  "proxy-authorization",
  "x-relay-target",
  "x-relay-host",
  "x-forwarded-for",
  "x-forwarded-proto",
  "x-forwarded-host",
  "x-cloud-trace-context",
  "forwarded",
  "traceparent",
  "user-agent",
]);

const MAX_BODY_BYTES = 24 * 1024 * 1024;

export const relay = onRequest(
  {
    region: "us-central1",
    secrets: ALL_SECRETS,
    // Image generation regularly runs past a minute.
    timeoutSeconds: 300,
    // Multipart try-on uploads carry several full-resolution JPEGs.
    memory: "512MiB",
    // A hard ceiling on how much this can ever cost in a runaway month.
    maxInstances: 20,
    cors: false,
  },
  async (req, res) => {
    // --- 1. Who is calling? ---
    const bearer = req.get("authorization");
    if (!bearer?.startsWith("Bearer ")) {
      res.status(401).json({ error: "Missing Firebase ID token" });
      return;
    }

    let uid: string;
    try {
      uid = (await getAuth().verifyIdToken(bearer.slice(7))).uid;
    } catch {
      res.status(401).json({ error: "Invalid Firebase ID token" });
      return;
    }

    // --- 2. What are they asking us to call? ---
    const targetName = req.get("x-relay-target") ?? "";
    const target = TARGETS[targetName];
    if (!target) {
      res.status(400).json({ error: `Unknown relay target '${targetName}'` });
      return;
    }

    // Depending on how the function is addressed the path may or may not still
    // carry the function name; both forms must resolve to the upstream path.
    const raw = req.originalUrl || req.url || "/";
    const [pathPart, queryPart = ""] = raw.split("?");
    const path = pathPart.replace(/^\/relay(?=\/|$)/, "") || "/";

    if (!target.allow.some((re) => re.test(path))) {
      logger.warn("blocked path", { uid, targetName, path });
      res.status(403).json({ error: `Path not allowed for '${targetName}'` });
      return;
    }

    // --- 3. Build the upstream URL ---
    let origin = target.origin;
    if (!origin) {
      const host = req.get("x-relay-host") ?? "";
      if (!target.hostAllow?.includes(host)) {
        logger.warn("blocked host", { uid, targetName, host });
        res.status(403).json({ error: `Host not allowed for '${targetName}'` });
        return;
      }
      origin = `https://${host}`;
    }

    const url = new URL(origin + path + (queryPart ? `?${queryPart}` : ""));

    const headers = new Headers();
    for (const [k, v] of Object.entries(req.headers)) {
      if (STRIP.has(k.toLowerCase()) || v === undefined) continue;
      headers.set(k, Array.isArray(v) ? v.join(", ") : v);
    }

    const rawBody =
      req.method === "GET" || req.method === "HEAD"
        ? undefined
        : (req.rawBody as Buffer | undefined);

    if (rawBody && rawBody.byteLength > MAX_BODY_BYTES) {
      res.status(413).json({ error: "Request body too large" });
      return;
    }

    // fetch() accepts an ArrayBufferView but not Node's Buffer subclass.
    const body = rawBody ? new Uint8Array(rawBody) : undefined;

    // --- 4. Charge before spending, refund if the spend never happened ---
    const units = target.weigh(path);
    try {
      await charge(uid, units);
    } catch (e) {
      if (e instanceof QuotaExceeded) {
        res.status(429).json({
          error: "quota_exceeded",
          message: e.message,
          used: e.used,
          limit: e.limit,
        });
        return;
      }
      throw e;
    }

    try {
      await target.authorize(headers, url);

      const upstream = await fetch(url, { method: req.method, headers, body });
      const buf = Buffer.from(await upstream.arrayBuffer());

      // An upstream failure is their fault, not the user's budget.
      if (upstream.status >= 500) await refund(uid, units);

      const type = upstream.headers.get("content-type");
      if (type) res.set("content-type", type);
      res.status(upstream.status).send(buf);
    } catch (err) {
      await refund(uid, units);
      logger.error("relay failed", { uid, targetName, path, err: String(err) });
      res.status(502).json({ error: "Upstream request failed" });
    }
  }
);
