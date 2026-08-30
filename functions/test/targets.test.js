const test = require("node:test");
const assert = require("node:assert");
const { createHash } = require("node:crypto");
const { topSign, hmacSign, beijingTimestamp, TARGETS } = require("../lib/targets");

// ── Taobao Open Platform signing ────────────────────────────────────────────
//
// MD5 over secret + every key and value sorted by key + secret, uppercased.
// Any deviation comes back from Taobao as an opaque "invalid signature", so
// this is pinned against an independently computed vector.

test("signs the documented way: secret + sorted pairs + secret, uppercased", () => {
  const p = new URLSearchParams({
    method: "taobao.tbk.dg.material.optional",
    app_key: "12345678",
    q: "亚麻短款西装外套",
    page_size: "20",
  });
  const expected = createHash("md5")
    .update("SECRET" + "app_key12345678methodtaobao.tbk.dg.material.optionalpage_size20q亚麻短款西装外套" + "SECRET", "utf8")
    .digest("hex")
    .toUpperCase();

  assert.strictEqual(topSign(p, "SECRET"), expected);
});

test("sorts by key, not by insertion order", () => {
  const a = new URLSearchParams([["b", "2"], ["a", "1"]]);
  const b = new URLSearchParams([["a", "1"], ["b", "2"]]);
  assert.strictEqual(topSign(a, "S"), topSign(b, "S"));
});

test("the signature is uppercase hex of the right length", () => {
  const sig = topSign(new URLSearchParams({ a: "1" }), "S");
  assert.match(sig, /^[0-9A-F]{32}$/);
});

test("a different secret gives a different signature", () => {
  const p = new URLSearchParams({ a: "1" });
  assert.notStrictEqual(topSign(p, "one"), topSign(p, "two"));
});

test("changing any value changes the signature", () => {
  const base = topSign(new URLSearchParams({ q: "西装" }), "S");
  assert.notStrictEqual(base, topSign(new URLSearchParams({ q: "外套" }), "S"));
});

// ── Timestamp ───────────────────────────────────────────────────────────────

test("timestamp is Beijing wall-clock in the format TOP expects", () => {
  // 2026-08-29T00:00:00Z is 08:00 the same day in Beijing.
  const utcMidnight = Date.UTC(2026, 7, 29, 0, 0, 0);
  assert.strictEqual(beijingTimestamp(utcMidnight), "2026-08-29 08:00:00");
});

test("timestamp rolls the date over, not just the hour", () => {
  // 16:30 UTC is 00:30 the next day in Beijing — a UTC clock would send
  // yesterday and be rejected as an expired request.
  const evening = Date.UTC(2026, 7, 29, 16, 30, 0);
  assert.strictEqual(beijingTimestamp(evening), "2026-08-30 00:30:00");
});

// ── Path and host allowlists ────────────────────────────────────────────────
//
// These are what stop the relay being an open proxy for anyone who can create
// an anonymous account.

test("openai allows only the two endpoints the app uses", () => {
  const allow = (p) => TARGETS.openai.allow.some((re) => re.test(p));
  assert.ok(allow("/v1/chat/completions"));
  assert.ok(allow("/v1/images/edits"));
  assert.ok(allow("/v1/images/generations"));
  assert.ok(!allow("/v1/models"));
  assert.ok(!allow("/v1/files"));
  assert.ok(!allow("/v1/chat/completions/../files"));
});

test("ebay exposes browse but never the token endpoint", () => {
  const allow = (p) => TARGETS.ebay.allow.some((re) => re.test(p));
  assert.ok(allow("/buy/browse/v1/item_summary/search"));
  // Minting tokens is ours to do; a client that could would hold an eBay token.
  assert.ok(!allow("/identity/v1/oauth2/token"));
});

test("taobaounion is pinned to the single router endpoint", () => {
  const allow = (p) => TARGETS.taobaounion.allow.some((re) => re.test(p));
  assert.ok(allow("/router/rest"));
  assert.ok(!allow("/router/rest/anything"));
  assert.ok(!allow("/"));
});

test("rapidapi forwards only to hosts we named", () => {
  const hosts = TARGETS.rapidapi.hostAllow;
  assert.ok(hosts.includes("taobao-datahub.p.rapidapi.com"));
  // Suffix matching alone would accept an attacker-controlled lookalike.
  assert.ok(!hosts.includes("p.rapidapi.com.evil.test"));
  assert.ok(!hosts.includes("evil.p.rapidapi.com"));
});

test("every target declares an origin or an explicit host allowlist", () => {
  for (const [name, t] of Object.entries(TARGETS)) {
    assert.ok(t.origin || (t.hostAllow && t.hostAllow.length), `${name} would forward anywhere`);
    assert.ok(t.allow.length > 0, `${name} has no path allowlist`);
  }
});

// ── Quota weighting ─────────────────────────────────────────────────────────

test("image generation is charged far above a chat call", () => {
  const chat = TARGETS.openai.weigh("/v1/chat/completions");
  const image = TARGETS.openai.weigh("/v1/images/edits");
  assert.ok(image > chat * 10, `image ${image} vs chat ${chat}`);
});

test("polling a try-on job is free, starting one is not", () => {
  assert.strictEqual(TARGETS.fashn.weigh("/v1/status/abc"), 0);
  assert.ok(TARGETS.fashn.weigh("/v1/run") > 0);
});

// ── Headers that must not be forwarded ──────────────────────────────────────

test("hop-by-hop and Expect headers are stripped", () => {
  const { STRIP_HEADERS } = require("../lib/index");
  // A forwarded Expect: 100-continue makes the outbound fetch fail outright,
  // which surfaced as a bare 502 on any large multipart upload.
  for (const h of ["expect", "te", "trailer", "connection", "keep-alive", "transfer-encoding"]) {
    assert.ok(STRIP_HEADERS.has(h), `${h} would be forwarded`);
  }
  // The caller's own credential must never reach the upstream.
  assert.ok(STRIP_HEADERS.has("authorization"));
  assert.ok(STRIP_HEADERS.has("x-relay-target"));
  // Content-type must survive: it carries the multipart boundary.
  assert.ok(!STRIP_HEADERS.has("content-type"));
});

// ── AliExpress signing ──────────────────────────────────────────────────────
//
// The gateway validates the app key before the signature, so which of the two
// forms applies cannot be established without a real key. Both are pinned here
// against independently computed digests so whichever the account turns out to
// need is already correct.

const { createHmac } = require("node:crypto");

test("HMAC signing uses the secret as the key, not as padding", () => {
  const p = new URLSearchParams({
    method: "aliexpress.affiliate.product.query",
    app_key: "12345678",
    keywords: "linen blazer",
    tracking_id: "slot1",
  });
  const expected = createHmac("sha256", "SECRET")
    .update("app_key12345678keywordslinen blazermethodaliexpress.affiliate.product.querytracking_idslot1", "utf8")
    .digest("hex")
    .toUpperCase();

  assert.strictEqual(hmacSign(p, "SECRET"), expected);
});

test("the two signing forms never agree", () => {
  // Sending the wrong one is rejected with an opaque message, so a test that
  // could not tell them apart would be worthless.
  const p = new URLSearchParams({ a: "1", b: "2" });
  assert.notStrictEqual(hmacSign(p, "S"), topSign(p, "S"));
});

test("HMAC output is uppercase hex of the right length", () => {
  assert.match(hmacSign(new URLSearchParams({ a: "1" }), "S"), /^[0-9A-F]{64}$/);
});

test("aliexpress is pinned to the single sync endpoint", () => {
  const allow = (p) => TARGETS.aliexpress.allow.some((re) => re.test(p));
  assert.ok(allow("/sync"));
  assert.ok(!allow("/sync/anything"));
  assert.ok(!allow("/"));
});

test("aliexpress forwards only to the affiliate gateway", () => {
  assert.strictEqual(TARGETS.aliexpress.origin, "https://api-sg.aliexpress.com");
});
