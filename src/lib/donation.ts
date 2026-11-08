/**
 * Support-the-author reminder schedule. Reminders fire when the number of days since install
 * reaches a milestone: 3, 7, 14, 30, then every 30 days (60, 90, …).
 *
 * Two channels, both opt-out in Settings → General → "Support reminders":
 *  - a local system notification scheduled for the milestone day (lib/localNotifications.ts);
 *  - an in-app prompt shown once on the first launch at or after the milestone.
 */
const FIXED = [3, 7, 14, 30];
const DAY = 86400000;

export function isMilestone(days: number): boolean {
  return FIXED.includes(days) || (days > 30 && days % 30 === 0);
}

/** Milestones after `afterDay`, up to `count` of them. */
export function nextMilestones(afterDay: number, count: number): number[] {
  const out: number[] = [];
  for (let d = Math.max(afterDay + 1, 1); out.length < count; d++) if (isMilestone(d)) out.push(d);
  return out;
}

/** Whole local calendar days between install and `now`. */
export function daysSinceInstall(createdAt: number, now = Date.now()): number {
  const a = new Date(createdAt);
  const b = new Date(now);
  const start = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate());
  const end = Date.UTC(b.getFullYear(), b.getMonth(), b.getDate());
  return Math.max(0, Math.round((end - start) / DAY));
}

/** The latest reached milestone that was not shown yet, or null. */
export function pendingMilestone(days: number, shown: number[]): number | null {
  for (let d = days; d >= 3; d--) {
    if (isMilestone(d)) return shown.includes(d) ? null : d;
  }
  return null;
}

/** Local date (midday) of a milestone, relative to the install date. */
export function milestoneDate(createdAt: number, milestone: number, hour = 12): Date {
  const a = new Date(createdAt);
  return new Date(a.getFullYear(), a.getMonth(), a.getDate() + milestone, hour, 0, 0);
}
