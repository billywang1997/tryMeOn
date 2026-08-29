/**
 * Google Play purchase verification.
 *
 * The relay's paid tier costs us real money per call, so entitlement cannot be
 * something the client asserts — a rooted device could just say "I paid". The
 * app sends the purchase token it got from Play, we check it against the Play
 * Developer API, and only then write `relayQuota/{uid}.paid`, which
 * firestore.rules makes unwritable from any client.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { GoogleAuth } from "google-auth-library";

/** Must match applicationId in app/build.gradle.kts. */
const PACKAGE_NAME = "com.example.myapplication";

/** Product ids that grant the paid relay tier. */
const PAID_PRODUCTS = new Set(["audit_unlock"]);

const auth = new GoogleAuth({
  scopes: ["https://www.googleapis.com/auth/androidpublisher"],
});

interface ProductPurchase {
  /** 0 = purchased, 1 = cancelled, 2 = pending. */
  purchaseState?: number;
  /** 0 = yet to be acknowledged, 1 = acknowledged. */
  acknowledgementState?: number;
  orderId?: string;
}

async function playApi(path: string, method: "GET" | "POST"): Promise<unknown> {
  const client = await auth.getClient();
  const res = await client.request({
    url: `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${path}`,
    method,
  });
  return res.data;
}

export const verifyPurchase = onCall(
  { region: "us-central1", timeoutSeconds: 60, maxInstances: 10 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign-in required");

    const productId = String(request.data?.productId ?? "");
    const purchaseToken = String(request.data?.purchaseToken ?? "");
    if (!PAID_PRODUCTS.has(productId) || !purchaseToken) {
      throw new HttpsError("invalid-argument", "Unknown product or missing token");
    }

    const db = getFirestore();

    // One purchase token entitles exactly one account. Without this, a single
    // real purchase could be replayed across unlimited anonymous accounts.
    const claimRef = db.collection("purchaseClaims").doc(purchaseToken);
    const claim = await claimRef.get();
    if (claim.exists && claim.data()?.uid !== uid) {
      logger.warn("purchase token replay", { uid, productId });
      throw new HttpsError("permission-denied", "This purchase belongs to another account");
    }

    let purchase: ProductPurchase;
    try {
      purchase = (await playApi(
        `${PACKAGE_NAME}/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}`,
        "GET"
      )) as ProductPurchase;
    } catch (err) {
      logger.error("Play verification failed", { uid, productId, err: String(err) });
      throw new HttpsError("internal", "Could not verify purchase with Google Play");
    }

    if (purchase.purchaseState !== 0) {
      throw new HttpsError("failed-precondition", "Purchase is not in a completed state");
    }

    // Acknowledge here rather than trusting the client to: Play auto-refunds an
    // unacknowledged purchase after three days.
    if (purchase.acknowledgementState === 0) {
      await playApi(
        `${PACKAGE_NAME}/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
        "POST"
      ).catch((err) => logger.error("acknowledge failed", { uid, err: String(err) }));
    }

    await claimRef.set({
      uid,
      productId,
      orderId: purchase.orderId ?? null,
      claimedAt: FieldValue.serverTimestamp(),
    });

    await db.collection("relayQuota").doc(uid).set(
      { paid: true, paidSince: FieldValue.serverTimestamp() },
      { merge: true }
    );

    logger.info("purchase verified", { uid, productId, orderId: purchase.orderId });
    return { paid: true, productId };
  }
);
