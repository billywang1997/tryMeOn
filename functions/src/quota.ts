/**
 * Per-user daily spend cap.
 *
 * The relay is only as safe as its cheapest abuse path: without a cap, one
 * account can sign in anonymously and burn the whole OpenAI balance in an
 * afternoon. Every call is charged in abstract "units" (see Target.weigh) and
 * a user gets a fixed budget per UTC day.
 *
 * The paid/free tier is read from the same document the transaction already
 * touches, so entitlement costs no extra read. Nothing here grants it — set
 * `relayQuota/{uid}.paid` from your purchase-verification path.
 */
import { getFirestore, FieldValue } from "firebase-admin/firestore";

/** Units per user per day for a free account. ~2 try-on images, or 60 chat calls. */
const FREE_DAILY_UNITS = Number(process.env.RELAY_FREE_DAILY_UNITS ?? 60);
/** Units per day once the user has bought anything. */
const PAID_DAILY_UNITS = Number(process.env.RELAY_PAID_DAILY_UNITS ?? 600);

export class QuotaExceeded extends Error {
  constructor(readonly used: number, readonly limit: number) {
    super(`Daily quota exhausted (${used}/${limit} units)`);
  }
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Charge `units` against the user's daily budget.
 *
 * Runs as a transaction so parallel requests from the same account cannot both
 * read the same "under the limit" value and each proceed. A zero-cost call
 * (e.g. polling a job status) skips the write entirely.
 */
export async function charge(uid: string, units: number): Promise<void> {
  if (units <= 0) return;

  const ref = getFirestore().collection("relayQuota").doc(uid);
  const day = today();

  await getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data();
    const limit = data?.paid === true ? PAID_DAILY_UNITS : FREE_DAILY_UNITS;
    // A record from a previous day is stale; the budget resets rather than accumulates.
    const used = data?.day === day ? (data.units as number) ?? 0 : 0;

    if (used + units > limit) throw new QuotaExceeded(used, limit);

    tx.set(
      ref,
      { day, units: used + units, updatedAt: FieldValue.serverTimestamp() },
      { merge: true }
    );
  });
}

/** Refund a charge when the upstream call failed and cost us nothing. */
export async function refund(uid: string, units: number): Promise<void> {
  if (units <= 0) return;
  const ref = getFirestore().collection("relayQuota").doc(uid);
  const day = today();
  await getFirestore()
    .runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const data = snap.data();
      if (data?.day !== day) return;
      const used = (data.units as number) ?? 0;
      tx.set(ref, { day, units: Math.max(0, used - units) }, { merge: true });
    })
    .catch(() => {
      // A failed refund must never turn into a failed response to the client.
    });
}
