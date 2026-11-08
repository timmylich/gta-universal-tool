/**
 * Support-the-author reminder schedule: an in-app prompt shown once when the number of days since
 * install reaches a milestone: 3, 7, 14, 30, then every 30 days (60, 90, …).
 */
package io.github.timmylich.gtatool.lib

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val FIXED = listOf(3, 7, 14, 30)

fun isMilestone(days: Int) = days in FIXED || (days > 30 && days % 30 == 0)

/** Whole local calendar days between install and [now]. */
fun daysSinceInstall(installedAt: Long, now: Long = System.currentTimeMillis()): Int {
  val z = ZoneId.systemDefault()
  val a = Instant.ofEpochMilli(installedAt).atZone(z).toLocalDate()
  val b = Instant.ofEpochMilli(now).atZone(z).toLocalDate()
  return maxOf(0, ChronoUnit.DAYS.between(a, b).toInt())
}

/** The latest reached milestone that was not shown yet, or null. */
fun pendingMilestone(days: Int, shown: List<Int>): Int? {
  for (d in days downTo 3) if (isMilestone(d)) return if (d in shown) null else d
  return null
}
