# API relay

Every third-party key the app uses — OpenAI, FASHN, SerpAPI, ScraperAPI,
RapidAPI, Google Custom Search, Unsplash, eBay — used to be compiled into
`BuildConfig` and shipped inside the APK. Anyone who unzipped a release build
could read them and spend our balance. This function is where they live now.

The app sends its request to the relay instead of the upstream, identifying
itself with a Firebase ID token. The relay checks the token, checks the caller's
daily spend budget, injects the real key, and forwards the call.

## Layout

| File | What it does |
| --- | --- |
| `src/index.ts` | Auth, path/host allowlisting, header scrubbing, forwarding |
| `src/targets.ts` | One entry per upstream: origin, allowed paths, how to attach the key, quota weight |
| `src/quota.ts` | Per-user daily unit budget, charged transactionally |

Anything not declared in `targets.ts` is rejected before a byte leaves the
function — that is what keeps this from being an open proxy for anyone who can
create an anonymous Firebase account.

## Quota

Calls are charged in abstract units, roughly proportional to what they cost us:

| Call | Units |
| --- | --- |
| `openai` image edit/generation | 25 |
| `fashn` try-on run | 20 |
| `serpapi` / `scraperapi` / `rapidapi` search | 2 |
| `openai` chat, `googlesearch`, `unsplash`, `ebay` | 1 |
| `fashn` status poll | 0 |

Defaults: 60 units/day free (about two try-on images), 600 for a paid account.
Override with `RELAY_FREE_DAILY_UNITS` / `RELAY_PAID_DAILY_UNITS`.

Nothing here grants the paid tier. Set `relayQuota/{uid}.paid = true` from
whatever verifies a Play purchase; `firestore.rules` denies clients all access
to that collection, so a user cannot lift their own cap.

## Testing

`npm test` covers signing, the allowlists and the header scrub. `npm run
test:rules` runs the Firestore rules against the emulator.

The relay's HTTP behaviour — auth, allowlists, quota, forwarding, large
multipart uploads — can be exercised locally without deploying and without
the Blaze plan. See [TESTING.md](../TESTING.md) for the emulator recipe and
what each response means.

Two things there that are easy to get wrong:

- Emulator ports must stay declared in `firebase.json`. Without them the auth
  emulator does not start, `verifyIdToken` silently reaches production, and a
  local test either fails confusingly or creates real users.
- Throwaway upstream values go in `functions/.secret.local`, which is
  gitignored. The functions need *something* to inject; that the upstream then
  rejects it is the proof that injection happened server-side.

## Deploy

Cloud Functions requires the **Blaze** (pay-as-you-go) plan.

```sh
npm --prefix functions install

# One-off: load each key into Secret Manager.
for s in OPENAI_API_KEY FASHN_API_KEY SERP_API_KEY SCRAPER_API_KEY RAPID_API_KEY \
         GOOGLE_SEARCH_KEY GOOGLE_SEARCH_CX UNSPLASH_ACCESS_KEY \
         EBAY_CLIENT_ID EBAY_CLIENT_SECRET \
         ALIEXPRESS_APP_KEY ALIEXPRESS_APP_SECRET ALIEXPRESS_TRACKING_ID \
         ALIEXPRESS_SIGN_METHOD; do
  firebase functions:secrets:set "$s"
done

firebase deploy --only functions,firestore:rules
```

Then put the printed URL in `local.properties`:

```
RELAY_BASE_URL=https://us-central1-mycloset-ce07e.cloudfunctions.net/relay
```

`app/build.gradle.kts` blanks every upstream key in **release** builds as soon
as that is set. Verify before shipping:

```sh
./gradlew :app:assembleRelease
strings app/build/outputs/apk/release/app-release.apk | grep -E 'sk-|Bearer '
```

## Adding an upstream

Add an entry to `targets.ts` with its origin, path allowlist, how the
credential attaches, and a quota weight proportional to what a call costs.
Nothing not declared there can be reached, which is deliberate: the relay is
otherwise an open proxy for anyone able to create an anonymous account.

Then add the target to `test/targets.test.js` — at minimum, that its
allowlist refuses a path the app does not use. `taobaounion` is the worked
example of a target that signs rather than just attaching a header.

## Which signature AliExpress wants

The gateway validates the app key before the signature, so the correct form
cannot be established without a real key. Both are implemented and tested:
set `ALIEXPRESS_SIGN_METHOD` to `sha256` (current gateway) and, if calls come
back rejected on signature rather than on key, switch it to `md5`. No code
change either way.

## Rotate the old keys

Every key that was ever in a `BuildConfig` of a distributed build should be
treated as burned. Rotate them at the provider, then load the new values into
Secret Manager — moving them server-side does not un-leak the old ones.
