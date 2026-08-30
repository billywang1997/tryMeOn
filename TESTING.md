# Testing

Four suites, in rough order of how long they take.

Command-line Gradle needs a JDK, and this machine has none on `PATH` —
Android Studio's bundled one works:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## App unit tests

```sh
./gradlew :app:testDebugUnitTest
```

Pure logic: landed cost, Taobao freight rules, FX policy, query and price
parsing, the translation cache, price matching. No device, no network.

Two things about this suite worth knowing:

- `android.jar` stubs throw on every call, so `testOptions.unitTests.
  isReturnDefaultValues` is on. Without it a parser that logs when it swallows
  a malformed payload fails the very tests that feed it malformed payloads.
- `org.json` is one of those stubs, which made JSON parsers report every input
  as unparseable. A real `org.json` is on the unit-test classpath instead.

## Relay function

```sh
cd functions && npm test
```

Signing, allowlists, quota weights and the header scrub. The Taobao signature
is pinned against an independently computed digest, because a wrong one comes
back from Taobao as "invalid signature" and nothing else.

## Firestore rules

```sh
cd functions && npm run test:rules
```

Runs against the Firestore emulator. Rules moved from "any signed-in user may
read and write everything" to per-uid ownership, and the app signs people in
anonymously — so *signed in* is not an authorisation check, and these assert
that from both sides: an owner can reach every path the app really uses, and a
second ordinary account can reach none of them.

Add a case here whenever `FirestoreRepository` touches a new collection. A path
with no rule is denied, so the failure shows up as sync silently not working.

## Storage rules

```sh
cd functions && npm run test:storage
```

Runs against the Storage emulator. These paths hold photographs of people,
including the face and body shots taken for try-on. A new bucket's default rule
is `request.auth != null`, and this app signs people in anonymously — so that
default would let anyone who installs the app read everyone else's photos by
walking the uid paths.

Two things the tests pinned down that reading the rules would not have:

- `allow write` covers delete, and on a delete there is no `request.resource`.
  A predicate that reads `request.resource.contentType` therefore fails the
  whole rule and denies the owner their own file. Create/update and delete are
  separate rules for that reason.
- A content-type check was tried and dropped. A client that sends no metadata
  gets `application/octet-stream`, so the rule would have had to accept that
  anyway, and an owner could name a zip `.jpg` regardless. It risked the worst
  failure available here — silently rejecting every photo upload — for nothing.

`storage.rules` is version-controlled and wired into `firebase.json`; before
this it existed only in the console and had never been verified.

## Device tests

Needs a running emulator or device.

```sh
./gradlew :app:connectedDebugAndroidTest
```

That uninstalls the app afterwards, which makes pulling files off it
impossible. To keep it installed and run one class:

```sh
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.trymeon.app.data.sourcing.SourcingLiveTest \
  com.trymeon.app.test/androidx.test.runner.AndroidJUnitRunner
```

`println` output goes to logcat, not to the Gradle console:

```sh
adb logcat -d | grep "System.out" | sed 's/.*System.out: //'
```

Tests that render something write it to the app's internal storage. External
storage cannot be read by `adb shell` on current Android, so pull with
`run-as`:

```sh
adb exec-out run-as com.trymeon.app cat files/cards/live_look.png > look.png
```

### The ones that spend money

`SourcingLiveTest`, `VirtualModelLiveTest` and `FxRateLiveTest` call live APIs
with the keys in `local.properties`, and skip themselves when those are absent.
`VirtualModelLiveTest` costs about three image generations on a cold run and
reuses what it already produced after that.

They exist because the failures that matter here do not show up in unit tests.
The scraper's payload has no top-level price field — it is under `sku.def` —
so every listing was being dropped for having no price, while the code
"succeeded". Only running it found that.

## Relay against the emulator

The relay's own HTTP behaviour — auth, allowlists, quota, forwarding — can be
exercised without deploying and without a Blaze plan.

Put throwaway values in `functions/.secret.local` (gitignored) so the
functions have something to inject:

```
OPENAI_API_KEY=test-openai
RAPID_API_KEY=test-rapid
TAOBAO_APP_KEY=test-taobao-key
TAOBAO_APP_SECRET=test-taobao-secret
TAOBAO_ADZONE_ID=99999
```

```sh
npx firebase emulators:start --only functions,auth,firestore --project mycloset-ce07e
```

The emulator ports are declared in `firebase.json`. They have to be: without
them the auth emulator does not start, and `verifyIdToken` reaches production
instead — which fails confusingly, or creates real users.

Mint a token the way the app does, then call the relay with it:

```sh
TOKEN=$(curl -s -X POST \
  "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake" \
  -H "Content-Type: application/json" -d '{"returnSecureToken":true}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['idToken'])")

BASE="http://127.0.0.1:5001/mycloset-ce07e/us-central1/relay"

# Allowed, and forwarded: the reply comes from the upstream, complaining
# about the throwaway key — which is the proof it was injected server-side.
curl -s "$BASE/v1/chat/completions" -H "Authorization: Bearer $TOKEN" \
  -H "X-Relay-Target: openai" -X POST -d '{}'

# Refused before dispatch.
curl -s "$BASE/v1/models" -H "Authorization: Bearer $TOKEN" -H "X-Relay-Target: openai" -X POST -d '{}'
```

Expected: `400` for an unknown target, `403` for a path or host outside the
allowlist, `401` with no token or a forged one, and `429` on the third image
call — two spend 50 of a free account's 60 units.

An unknown target with no token must still answer `401`, not `400`. That is
what shows authentication runs before dispatch: an unauthenticated caller
cannot probe which upstreams exist.

### Large uploads

Try-on sends a multipart of several megabytes. Worth re-checking after any
change to header handling:

```sh
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/v1/images/edits" \
  -H "Authorization: Bearer $TOKEN" -H "X-Relay-Target: openai" \
  -F "model=gpt-image-1" -F "prompt=x" -F "image[]=@some-large.png"
```

`401` from the upstream is the pass: it parsed the body and only objected to
the key. A `502` means the relay itself failed — check the logged `cause`,
since fetch reports every transport problem as "fetch failed". A forwarded
`Expect: 100-continue`, which curl adds to large uploads, used to do exactly
that.

## Taobao Union without credentials

The affiliate API needs an Alimama account, but the request can still be
checked against the live router with a deliberately invalid key:

```sh
cd functions && node -e '
const { topSign, beijingTimestamp } = require("./lib/targets");
const p = new URLSearchParams({
  method: "taobao.tbk.dg.material.optional", q: "亚麻短款西装外套",
  page_size: "20", page_no: "1", adzone_id: "99999", app_key: "0000000000",
  format: "json", v: "2.0", sign_method: "md5", timestamp: beijingTimestamp(),
});
p.set("sign", topSign(p, "not-a-real-secret"));
fetch("https://eco.taobao.com/router/rest?" + p).then(r => r.text()).then(console.log);
'
```

`{"error_response":{"code":29,"msg":"Invalid app Key"}}` is the pass: the
method, parameters, timestamp and signature format all reached credential
checking intact. A complaint about anything else means the request is wrong.

## What still cannot be checked here

- **A real Taobao Union call.** Needs an appkey. The probe above gets as far
  as anything can without one.
- **A real Play purchase.** `verifyPurchase` is checked as far as the Play API
  call, which fails locally for want of credentials — and correctly grants
  nothing when it does.
- **Whether the portrait looks like you.** The device test synthesises a face,
  which proves the same person survives from portrait into try-on, not that it
  resembles anyone in particular. Upload a photo on the TRY ON tab and judge it.
