const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { ref, uploadBytes, getBytes, deleteObject } = require("firebase/storage");

// These are photographs of people, including face and body shots taken for
// try-on. A new bucket's default rule is `request.auth != null`, and this app
// signs people in anonymously, so that default would let anyone who installs
// the app read everyone else's photos. The rules were tightened; this checks
// the tightening from both sides — an owner can still do everything the app
// needs, and a second ordinary account can do none of it.

let env;
const ALICE = "alice-uid";
const BOB = "bob-uid";

const alice = () => env.authenticatedContext(ALICE).storage();
const bob = () => env.authenticatedContext(BOB).storage();
const stranger = () => env.unauthenticatedContext().storage();

const jpeg = () => new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0x00, 0x01]);
const asPhoto = { contentType: "image/jpeg" };

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "rules-test",
    storage: {
      rules: fs.readFileSync(path.join(__dirname, "../../storage.rules"), "utf8"),
      host: "127.0.0.1",
      port: 9199,
    },
  });
});

test.beforeEach(async () => env.clearStorage());
test.after(async () => env && env.cleanup());

// Seeds a file bypassing rules, so a read test is not gated on a write test.
const seed = (p) =>
  env.withSecurityRulesDisabled((c) =>
    uploadBytes(ref(c.storage(), p), jpeg(), asPhoto)
  );

const PATHS = [
  ["a garment photo", `users/${ALICE}/wardrobe/1.jpg`],
  ["a face or body photo", `users/${ALICE}/profile/face.jpg`],
  ["a saved try-on render", `users/${ALICE}/saved/9.jpg`],
];

for (const [what, p] of PATHS) {
  test(`the owner can write and read ${what}`, async () => {
    await assertSucceeds(uploadBytes(ref(alice(), p), jpeg(), asPhoto));
    await assertSucceeds(getBytes(ref(alice(), p)));
    await assertSucceeds(deleteObject(ref(alice(), p)));
  });

  test(`another signed-in user cannot read ${what}`, async () => {
    await seed(p);
    await assertFails(getBytes(ref(bob(), p)));
  });

  test(`another signed-in user cannot overwrite ${what}`, async () => {
    await seed(p);
    await assertFails(uploadBytes(ref(bob(), p), jpeg(), asPhoto));
  });

  test(`another signed-in user cannot delete ${what}`, async () => {
    await seed(p);
    await assertFails(deleteObject(ref(bob(), p)));
  });

  test(`a signed-out caller cannot read ${what}`, async () => {
    await seed(p);
    await assertFails(getBytes(ref(stranger(), p)));
  });
}

test("a path no rule covers is denied rather than open", async () => {
  await assertFails(
    uploadBytes(ref(alice(), `users/${ALICE}/receipts/1.pdf`), jpeg(), asPhoto)
  );
  await assertFails(uploadBytes(ref(alice(), "public/1.jpg"), jpeg(), asPhoto));
});

test("an upload is capped by size, whatever type it claims", async () => {
  // There is no content-type check: a client that sends no metadata gets
  // application/octet-stream, and an owner could name a zip .jpg anyway. Size
  // and uid ownership are what actually hold, so both shapes are allowed here
  // and the cap below is the boundary that matters.
  await assertSucceeds(
    uploadBytes(ref(alice(), `users/${ALICE}/wardrobe/2.jpg`), jpeg())
  );
  await assertSucceeds(
    uploadBytes(ref(alice(), `users/${ALICE}/wardrobe/3.jpg`), jpeg(), asPhoto)
  );
});

test("an oversized upload is refused", async () => {
  const huge = new Uint8Array(16 * 1024 * 1024);
  await assertFails(
    uploadBytes(ref(alice(), `users/${ALICE}/wardrobe/big.jpg`), huge, asPhoto)
  );
});

// ── Shared fit looks ────────────────────────────────────────────────────────
//
// The one path here whose contents are meant to reach strangers, and it reaches
// them through the tokenised download URL held in the Firestore document rather
// than through these rules. So the rules stay owner-only: the URL is a
// capability the wearer hands out by publishing, and path access is not.

test("only the wearer can write their own fit look", async () => {
  const p = `fit_looks/${ALICE}/l1.jpg`;
  await assertSucceeds(uploadBytes(ref(alice(), p), jpeg(), asPhoto));
  await assertFails(uploadBytes(ref(bob(), p), jpeg(), asPhoto));
});

test("another signed-in user cannot reach the photo by path", async () => {
  const p = `fit_looks/${ALICE}/l1.jpg`;
  await seed(p);
  await assertFails(getBytes(ref(bob(), p)));
  await assertFails(getBytes(ref(stranger(), p)));
});

test("nobody else can delete the wearer's photo", async () => {
  const p = `fit_looks/${ALICE}/l1.jpg`;
  await seed(p);
  await assertFails(deleteObject(ref(bob(), p)));
  // The wearer must be able to, or a look can never really be taken down.
  await assertSucceeds(deleteObject(ref(alice(), p)));
});

test("a fit look is size capped like every other upload", async () => {
  const huge = new Uint8Array(16 * 1024 * 1024);
  await assertFails(uploadBytes(ref(alice(), `fit_looks/${ALICE}/big.jpg`), huge, asPhoto));
});

test("a look cannot be posted outside the wearer's own folder", async () => {
  await assertFails(uploadBytes(ref(alice(), `fit_looks/${BOB}/l1.jpg`), jpeg(), asPhoto));
});
