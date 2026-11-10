package io.github.timmylich.gtatool

import io.github.timmylich.gtatool.data.Settings
import io.github.timmylich.gtatool.data.Store
import io.github.timmylich.gtatool.i18n.Dict
import io.github.timmylich.gtatool.i18n.Lang
import io.github.timmylich.gtatool.i18n.bytes
import io.github.timmylich.gtatool.lib.pendingMilestone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppTest {
  private fun dict(l: Lang) = Dict(l, Json.parseToJsonElement(File("src/main/assets/i18n_${l.code}.json").readText()).jsonObject)

  /** Every literal key the code uses exists in both languages (templated keys like "home.$key" are skipped). */
  @Test fun dictionaryKeys() {
    val use = Regex("""(?:\bL|\bd|currentDict)(?:\[|\.t\(|\.n\()"([a-zA-Z0-9_.]+)"""")
    val keys = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f -> use.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
    assertTrue(keys.size > 50)
    for (l in Lang.entries) {
      val root = Json.parseToJsonElement(File("src/main/assets/i18n_${l.code}.json").readText()).jsonObject
      for (k in keys) {
        var cur: Any? = root
        for (p in k.split('.')) cur = (cur as? JsonObject)?.get(p)
        assertTrue("missing ${l.code}: $k", cur != null)
      }
    }
    // Keys built at runtime.
    for (l in Lang.entries) for (k in listOf("textures", "img", "audio", "adapt")) {
      val d = dict(l)
      assertTrue(d["home.$k"] != "home.$k" && d["home.${k}Hint"] != "home.${k}Hint")
    }
  }

  @Test fun plurals() {
    val ru = dict(Lang.Ru)
    assertEquals("1 текстура", ru.n("tex.textures", 1))
    assertEquals("3 текстуры", ru.n("tex.textures", 3))
    assertEquals("11 текстур", ru.n("tex.textures", 11))
    assertEquals("21 текстура", ru.n("tex.textures", 21))
    assertEquals("2 files", dict(Lang.En).n("common.files", 2))
    assertEquals("2 archivos", dict(Lang.Es).n("common.files", 2))
    assertEquals("2 file", dict(Lang.Id).n("common.files", 2))
    for (l in Lang.entries) assertEquals(0, dict(l).list("guide.items").size % 2)
    assertEquals("Added: 2\nReplaced: 0", dict(Lang.En).result(Triple("added", 2, true), Triple("replaced", 0, true), Triple("errors", 0, false)))
    assertEquals("Saved 3 to /x", dict(Lang.En).t("common.exportedTo", 3, "/x"))
  }

  /** The settings file written by the React Native version (0.1.0) still loads. */
  @Test fun oldSettings() {
    val old = """{"gameDir":"/storage/emulated/0/SFRP64","language":"ru","theme":"system","backup":true,"prefix":"car","texImport":{"format":"DXT5","mips":true,"rle":true,"maxSize":1024,"pot":true,"alphamode":"auto","camnorm":false,"streammode":false,"extra":"","overwrite":true},"lastDir":null,"installedAt":1761700000000,"donationShown":[3],"imgWarned":false,"future":1}"""
    val s = Store.json.decodeFromString<Settings>(old)
    assertEquals("/storage/emulated/0/SFRP64", s.gameDir)
    assertEquals("DXT5", s.texImport.format)
    assertEquals(1024, s.texImport.maxSize)
    assertEquals(listOf(3), s.donationShown)
  }

  @Test fun milestones() {
    assertNull(pendingMilestone(2, emptyList()))
    assertEquals(7, pendingMilestone(9, listOf(3)))
    assertNull(pendingMilestone(9, listOf(7)))
    assertEquals(60, pendingMilestone(61, emptyList()))
    assertEquals("1.5 KB", bytes(1536))
  }
}
