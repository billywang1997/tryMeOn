const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs } =
  require("firebase/firestore");

// Rules were tightened from "any signed-in user may read and write everything"
// to per-uid ownership. Syntax was checked; behaviour was not. Getting this
// wrong in either direction is bad — too loose exposes every user's wardrobe,
// too tight silently breaks cloud sync for everyone.

let env;
const ALICE = "alice-uid";
const BOB = "bob-uid";

// The app signs people in anonymously, so both of these are ordinary users.
const alice = () => env.authenticatedContext(ALICE).firestore();
const bob = () => env.authenticatedContext(BOB).firestore();
const stranger = () => env.unauthenticatedContext().firestore();

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "rules-test",
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, "../../firestore.rules"), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

test.beforeEach(async () => env.clearFirestore());
test.after(async () => env && env.cleanup());

// ── The paths the app actually uses ─────────────────────────────────────────

const OWN_PATHS = [
  ["users/alice-uid", "meta"],
  ["users/alice-uid/data/profile", "profile"],
  ["users/alice-uid/wardrobe/item1", "wardrobe"],
  ["users/alice-uid/history/log1", "outfit history"],
  ["users/alice-uid/saved/look1", "saved looks"],
  ["users/alice-uid/wishlist/w1", "wishlist"],
];

test("an owner can write and read every path the app uses", async () => {
  for (const [p, label] of OWN_PATHS) {
    await assertSucceeds(setDoc(doc(alice(), p), { v: 1 }));
    await assertSucceeds(getDoc(doc(alice(), p)));
    await assertSucceeds(updateDoc(doc(alice(), p), { v: 2 }));
    await assertSucceeds(deleteDoc(doc(alice(), p)));
  }
});

test("another signed-in user cannot touch any of them", async () => {
  // The whole point: being signed in is not authorisation when anyone can be.
  for (const [p, label] of OWN_PATHS) {
    await assertFails(getDoc(doc(bob(), p)));
    await assertFails(setDoc(doc(bob(), p), { v: 1 }));
  }
});

test("a signed-out client cannot touch them either", async () => {
  for (const [p] of OWN_PATHS) {
    await assertFails(getDoc(doc(stranger(), p)));
    await assertFails(setDoc(doc(stranger(), p), { v: 1 }));
  }
});

test("a nested path nobody anticipated is still owner-scoped", async () => {
  const deep = "users/alice-uid/wardrobe/item1/versions/v1/notes/n1";
  await assertSucceeds(setDoc(doc(alice(), deep), { v: 1 }));
  await assertFails(getDoc(doc(bob(), deep)));
});

test("listing another user's wardrobe is refused, not just reading one doc", async () => {
  await assertFails(getDocs(collection(bob(), "users/alice-uid/wardrobe")));
});

// ── Marketplace: public to browse, private to change ────────────────────────

test("anyone signed in may browse listings", async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), "market_listings/L1"), { sellerUid: ALICE, price: 10 });
  });
  await assertSucceeds(getDoc(doc(bob(), "market_listings/L1")));
  await assertSucceeds(getDocs(collection(bob(), "market_listings")));
  // Browsing still requires an account.
  await assertFails(getDoc(doc(stranger(), "market_listings/L1")));
});

test("a seller may post only as themselves", async () => {
  await assertSucceeds(setDoc(doc(alice(), "market_listings/L2"), { sellerUid: ALICE }));
  // Posting under someone else's name would let anyone attribute a listing.
  await assertFails(setDoc(doc(alice(), "market_listings/L3"), { sellerUid: BOB }));
  await assertFails(setDoc(doc(alice(), "market_listings/L4"), { price: 10 }));
});

test("only the seller may change or remove a listing", async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), "market_listings/L5"), { sellerUid: ALICE, price: 10 });
  });
  await assertSucceeds(updateDoc(doc(alice(), "market_listings/L5"), { price: 8 }));
  await assertFails(updateDoc(doc(bob(), "market_listings/L5"), { price: 1 }));
  await assertFails(deleteDoc(doc(bob(), "market_listings/L5")));
  await assertSucceeds(deleteDoc(doc(alice(), "market_listings/L5")));
});

test("a listing cannot be reassigned to another account", async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), "market_listings/L6"), { sellerUid: ALICE, price: 10 });
  });
  // Neither taking one over nor handing one off should be possible: ownership
  // is what the write rules are checked against.
  await assertFails(updateDoc(doc(bob(), "market_listings/L6"), { sellerUid: BOB }));
  await assertFails(updateDoc(doc(alice(), "market_listings/L6"), { sellerUid: BOB }));
});

// ── Server-only collections ─────────────────────────────────────────────────

test("spend counters are unreachable from any client", async () => {
  // A client that can write here can reset its own daily cap.
  for (const db of [alice(), bob(), stranger()]) {
    await assertFails(getDoc(doc(db, `relayQuota/${ALICE}`)));
    await assertFails(setDoc(doc(db, `relayQuota/${ALICE}`), { units: 0, paid: true }));
  }
});

test("purchase claims are unreachable from any client", async () => {
  for (const db of [alice(), bob(), stranger()]) {
    await assertFails(getDoc(doc(db, "purchaseClaims/token1")));
    await assertFails(setDoc(doc(db, "purchaseClaims/token1"), { uid: BOB }));
  }
});

test("a user cannot grant themselves the paid tier", async () => {
  await assertFails(setDoc(doc(alice(), `relayQuota/${ALICE}`), { paid: true }));
});

// ── Everything else ─────────────────────────────────────────────────────────

test("collections the app does not use are closed", async () => {
  for (const p of ["random/doc", "admin/config", "users", "logs/entry1"]) {
    await assertFails(setDoc(doc(alice(), p.includes("/") ? p : `${p}/x`), { v: 1 }));
  }
});
