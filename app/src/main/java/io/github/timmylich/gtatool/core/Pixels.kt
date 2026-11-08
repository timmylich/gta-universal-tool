package io.github.timmylich.gtatool.core

/**
 * Pixel codecs. Images are IntArray in ARGB order (the order of android.graphics.Bitmap and java BufferedImage).
 * DXT blocks follow the standard S3TC layout; 16/32-bit formats are little-endian as stored in GTA SA mobile texdb.
 */
object Pixels {
  // ---- small helpers ----
  private fun c565(c: Int): Int {
    val r = (c shr 11 and 31) * 255 / 31
    val g = (c shr 5 and 63) * 255 / 63
    val b = (c and 31) * 255 / 31
    return (r shl 16) or (g shl 8) or b
  }

  private fun to565(r: Int, g: Int, b: Int) = ((r * 31 + 127) / 255 shl 11) or ((g * 63 + 127) / 255 shl 5) or ((b * 31 + 127) / 255)

  private fun u16(d: ByteArray, o: Int) = (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)

  // ---- DXT decode ----
  private fun colorBlock(d: ByteArray, o: Int, out: IntArray, dxt1: Boolean) {
    val a = u16(d, o)
    val b = u16(d, o + 2)
    val ca = c565(a)
    val cb = c565(b)
    val p = IntArray(4)
    p[0] = ca or (255 shl 24)
    p[1] = cb or (255 shl 24)
    fun mix(x: Int, y: Int, wx: Int, wy: Int, div: Int): Int {
      val r = ((x shr 16 and 255) * wx + (y shr 16 and 255) * wy) / div
      val g = ((x shr 8 and 255) * wx + (y shr 8 and 255) * wy) / div
      val bb = ((x and 255) * wx + (y and 255) * wy) / div
      return (255 shl 24) or (r shl 16) or (g shl 8) or bb
    }
    if (a > b || !dxt1) {
      p[2] = mix(ca, cb, 2, 1, 3)
      p[3] = mix(ca, cb, 1, 2, 3)
    } else {
      p[2] = mix(ca, cb, 1, 1, 2)
      p[3] = 0
    }
    for (i in 0 until 16) {
      val bits = (d[o + 4 + i / 4].toInt() shr ((i % 4) * 2)) and 3
      out[i] = p[bits]
    }
  }

  /** Decodes a DXT1/3/5 image of w×h from [d] at [off]. kind: 1, 3 or 5. */
  fun decodeDxt(d: ByteArray, off: Int, w: Int, h: Int, kind: Int): IntArray {
    val out = IntArray(w * h)
    val bw = (w + 3) / 4
    val bh = (h + 3) / 4
    val bs = if (kind == 1) 8 else 16
    val blk = IntArray(16)
    val alpha = IntArray(16)
    var o = off
    for (by in 0 until bh) for (bx in 0 until bw) {
      if (o + bs > d.size) return out
      if (kind == 1) {
        colorBlock(d, o, blk, true)
        for (i in 0 until 16) alpha[i] = blk[i] ushr 24
      } else if (kind == 3) {
        for (i in 0 until 16) alpha[i] = ((d[o + i / 2].toInt() shr ((i % 2) * 4)) and 15) * 17
        colorBlock(d, o + 8, blk, false)
      } else {
        val a0 = d[o].toInt() and 255
        val a1 = d[o + 1].toInt() and 255
        val pal = IntArray(8)
        pal[0] = a0; pal[1] = a1
        if (a0 > a1) for (i in 1..6) pal[i + 1] = ((7 - i) * a0 + i * a1) / 7
        else {
          for (i in 1..4) pal[i + 1] = ((5 - i) * a0 + i * a1) / 5
          pal[6] = 0; pal[7] = 255
        }
        var bits = 0L
        for (i in 0 until 6) bits = bits or ((d[o + 2 + i].toLong() and 255) shl (8 * i))
        for (i in 0 until 16) alpha[i] = pal[((bits shr (3 * i)) and 7).toInt()]
        colorBlock(d, o + 8, blk, false)
      }
      for (i in 0 until 16) {
        val x = bx * 4 + i % 4
        val y = by * 4 + i / 4
        if (x < w && y < h) out[y * w + x] = (blk[i] and 0xFFFFFF) or (alpha[i] shl 24)
      }
      o += bs
    }
    return out
  }

  // ---- DXT encode (bounding-box fit with inset, good quality for its speed) ----
  private fun encodeColorBlock(px: IntArray, out: ByteArray, o: Int, dxt1: Boolean) {
    var minR = 255; var minG = 255; var minB = 255; var maxR = 0; var maxG = 0; var maxB = 0
    for (c in px) {
      if (dxt1 && (c ushr 24) < 128) continue
      val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
      if (r < minR) minR = r; if (g < minG) minG = g; if (b < minB) minB = b
      if (r > maxR) maxR = r; if (g > maxG) maxG = g; if (b > maxB) maxB = b
    }
    val hasTransparent = dxt1 && px.any { (it ushr 24) < 128 }
    if (minR > maxR) { minR = 0; minG = 0; minB = 0; maxR = 0; maxG = 0; maxB = 0 }
    // Inset the box by 1/16 to reduce error at the extremes.
    val ir = (maxR - minR) shr 4; val ig = (maxG - minG) shr 4; val ib = (maxB - minB) shr 4
    minR = minOf(255, minR + ir); minG = minOf(255, minG + ig); minB = minOf(255, minB + ib)
    maxR = maxOf(0, maxR - ir); maxG = maxOf(0, maxG - ig); maxB = maxOf(0, maxB - ib)
    var c0 = to565(maxR, maxG, maxB)
    var c1 = to565(minR, minG, minB)
    val pal = IntArray(4)
    if (hasTransparent) {
      // 3-colour mode: c0 <= c1, index 3 = transparent.
      if (c0 > c1) { val t = c0; c0 = c1; c1 = t }
      val a = c565(c0); val b = c565(c1)
      pal[0] = a; pal[1] = b
      pal[2] = (((a shr 16 and 255) + (b shr 16 and 255)) / 2 shl 16) or (((a shr 8 and 255) + (b shr 8 and 255)) / 2 shl 8) or (((a and 255) + (b and 255)) / 2)
    } else {
      if (c0 < c1) { val t = c0; c0 = c1; c1 = t }
      val a = c565(c0); val b = c565(c1)
      pal[0] = a; pal[1] = b
      for (k in 0..1) {
        val wa = if (k == 0) 2 else 1
        val wb = 3 - wa
        pal[2 + k] = ((((a shr 16 and 255) * wa + (b shr 16 and 255) * wb) / 3) shl 16) or
          ((((a shr 8 and 255) * wa + (b shr 8 and 255) * wb) / 3) shl 8) or (((a and 255) * wa + (b and 255) * wb) / 3)
      }
    }
    out[o] = c0.toByte(); out[o + 1] = (c0 shr 8).toByte(); out[o + 2] = c1.toByte(); out[o + 3] = (c1 shr 8).toByte()
    val n = if (hasTransparent) 3 else 4
    for (i in 0 until 16) {
      val c = px[i]
      var best = 0
      if (hasTransparent && (c ushr 24) < 128) best = 3
      else {
        var bd = Int.MAX_VALUE
        for (k in 0 until n) {
          val dr = (c shr 16 and 255) - (pal[k] shr 16 and 255)
          val dg = (c shr 8 and 255) - (pal[k] shr 8 and 255)
          val db = (c and 255) - (pal[k] and 255)
          val dist = dr * dr * 3 + dg * dg * 4 + db * db * 2
          if (dist < bd) { bd = dist; best = k }
        }
      }
      out[o + 4 + i / 4] = (out[o + 4 + i / 4].toInt() or (best shl ((i % 4) * 2))).toByte()
    }
  }

  private fun encodeAlphaBlock(px: IntArray, out: ByteArray, o: Int) {
    var lo = 255; var hi = 0
    for (c in px) { val a = c ushr 24; if (a < lo) lo = a; if (a > hi) hi = a }
    out[o] = hi.toByte(); out[o + 1] = lo.toByte()
    val pal = IntArray(8)
    pal[0] = hi; pal[1] = lo
    for (i in 1..6) pal[i + 1] = ((7 - i) * hi + i * lo) / 7
    var bits = 0L
    for (i in 0 until 16) {
      val a = px[i] ushr 24
      var best = 0; var bd = Int.MAX_VALUE
      if (hi != lo) for (k in 0 until 8) { val dd = Math.abs(a - pal[k]); if (dd < bd) { bd = dd; best = k } }
      bits = bits or (best.toLong() shl (3 * i))
    }
    for (i in 0 until 6) out[o + 2 + i] = (bits shr (8 * i)).toByte()
  }

  fun encodeDxt(px: IntArray, w: Int, h: Int, kind: Int): ByteArray {
    val bw = (w + 3) / 4
    val bh = (h + 3) / 4
    val bs = if (kind == 1) 8 else 16
    val out = ByteArray(bw * bh * bs)
    val blk = IntArray(16)
    var o = 0
    for (by in 0 until bh) for (bx in 0 until bw) {
      for (i in 0 until 16) {
        val x = minOf(w - 1, bx * 4 + i % 4)
        val y = minOf(h - 1, by * 4 + i / 4)
        blk[i] = px[y * w + x]
      }
      if (kind == 5) { encodeAlphaBlock(blk, out, o); encodeColorBlock(blk, out, o + 8, false) }
      else encodeColorBlock(blk, out, o, true)
      o += bs
    }
    return out
  }

  // ---- 16/32-bit ----
  /** fmt: "8888" (bytes R,G,B,A), "565", "4444" (R in high nibble, A low). */
  fun decodeRaw(d: ByteArray, off: Int, w: Int, h: Int, fmt: String): IntArray {
    val out = IntArray(w * h)
    for (i in 0 until w * h) {
      when (fmt) {
        "8888" -> {
          val o = off + i * 4
          if (o + 4 > d.size) return out
          out[i] = ((d[o + 3].toInt() and 255) shl 24) or ((d[o].toInt() and 255) shl 16) or ((d[o + 1].toInt() and 255) shl 8) or (d[o + 2].toInt() and 255)
        }
        "565" -> {
          val o = off + i * 2
          if (o + 2 > d.size) return out
          out[i] = c565(u16(d, o)) or (255 shl 24)
        }
        else -> {
          val o = off + i * 2
          if (o + 2 > d.size) return out
          val v = u16(d, o)
          out[i] = ((v and 15) * 17 shl 24) or ((v shr 12 and 15) * 17 shl 16) or ((v shr 8 and 15) * 17 shl 8) or ((v shr 4 and 15) * 17)
        }
      }
    }
    return out
  }

  fun encodeRaw(px: IntArray, fmt: String): ByteArray {
    val bpp = if (fmt == "8888") 4 else 2
    val out = ByteArray(px.size * bpp)
    for (i in px.indices) {
      val c = px[i]
      val a = c ushr 24; val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
      when (fmt) {
        "8888" -> { out[i * 4] = r.toByte(); out[i * 4 + 1] = g.toByte(); out[i * 4 + 2] = b.toByte(); out[i * 4 + 3] = a.toByte() }
        "565" -> { val v = to565(r, g, b); out[i * 2] = v.toByte(); out[i * 2 + 1] = (v shr 8).toByte() }
        else -> {
          val v = ((r * 15 + 127) / 255 shl 12) or ((g * 15 + 127) / 255 shl 8) or ((b * 15 + 127) / 255 shl 4) or ((a * 15 + 127) / 255)
          out[i * 2] = v.toByte(); out[i * 2 + 1] = (v shr 8).toByte()
        }
      }
    }
    return out
  }

  // ---- resampling ----
  /** Half-size box filter (for mip levels). */
  fun half(px: IntArray, w: Int, h: Int): IntArray {
    val nw = maxOf(1, w / 2); val nh = maxOf(1, h / 2)
    val out = IntArray(nw * nh)
    for (y in 0 until nh) for (x in 0 until nw) {
      var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
      for (dy in 0..1) for (dx in 0..1) {
        val sx = minOf(w - 1, x * 2 + dx); val sy = minOf(h - 1, y * 2 + dy)
        val c = px[sy * w + sx]
        a += c ushr 24; r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255; n++
      }
      out[y * nw + x] = (a / n shl 24) or (r / n shl 16) or (g / n shl 8) or (b / n)
    }
    return out
  }

  /** Bilinear resize. */
  fun resize(px: IntArray, w: Int, h: Int, nw: Int, nh: Int): IntArray {
    if (w == nw && h == nh) return px
    var cur = px; var cw = w; var ch = h
    // Halve first while far larger, so downscaling averages instead of skipping pixels.
    while (cw >= nw * 2 && ch >= nh * 2) { cur = half(cur, cw, ch); cw = maxOf(1, cw / 2); ch = maxOf(1, ch / 2) }
    if (cw == nw && ch == nh) return cur
    val out = IntArray(nw * nh)
    for (y in 0 until nh) {
      val fy = maxOf(0f, (y + 0.5f) * ch / nh - 0.5f)
      val y0 = minOf(ch - 1, fy.toInt()); val y1 = minOf(ch - 1, y0 + 1); val ty = fy - y0
      for (x in 0 until nw) {
        val fx = maxOf(0f, (x + 0.5f) * cw / nw - 0.5f)
        val x0 = minOf(cw - 1, fx.toInt()); val x1 = minOf(cw - 1, x0 + 1); val tx = fx - x0
        var res = 0
        for (s in 0..3) {
          val sh = s * 8
          fun ch(c: Int) = (c ushr sh and 255).toFloat()
          val top = ch(cur[y0 * cw + x0]) * (1 - tx) + ch(cur[y0 * cw + x1]) * tx
          val bot = ch(cur[y1 * cw + x0]) * (1 - tx) + ch(cur[y1 * cw + x1]) * tx
          res = res or ((top * (1 - ty) + bot * ty + 0.5f).toInt().coerceIn(0, 255) shl sh)
        }
        out[y * nw + x] = res
      }
    }
    return out
  }

  fun hasAlpha(px: IntArray) = px.any { (it ushr 24) != 255 }

  /** Is alpha only 0/255 (cutout)? */
  fun binaryAlpha(px: IntArray) = px.all { val a = it ushr 24; a == 0 || a == 255 }
}
