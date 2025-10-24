package com.multipart.utils

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.util.ByteString

import scala.annotation.tailrec

/** Helper functions for ByteString operations
  */
object ByteStringHelpers {
  def byteAt(input: ByteString, offset: Int): Byte =
    if (offset >= input.length) throw NotEnoughDataException else input(offset)

  def byteChar(input: ByteString, offset: Int): Char = byteAt(input, offset).toChar
}

/** Exception thrown when there's not enough data in the ByteString
  */
case object NotEnoughDataException extends Exception("Not enough data") {
  override def fillInStackTrace(): Throwable = this
}

/** Efficient Boyer–Moore search algorithm on bytes.
  *
  * This implementation provides O(n/m) average case performance for searching
  * a pattern (needle) in a text (haystack), where n is the haystack length
  * and m is the needle length.
  *
  * The algorithm uses two heuristics:
  *   - Bad character rule (charTable)
  *   - Good suffix rule (offsetTable)
  *
  * @param needle The pattern to search for (must be non-empty)
  */
final class BoyerMoore(needle: Array[Byte]) extends LazyLogging {
  require(needle.nonEmpty, "needle must be non-empty")

  private[this] val nl1 = needle.length - 1

  /** Bad character table: maps each byte value to its shift distance
    */
  private[this] val charTable = {
    val t = Array.fill(256)(needle.length)
    @tailrec def rec(i: Int): Unit =
      if (i < nl1) { t(needle(i) & 0xff) = nl1 - i; rec(i + 1) }
    rec(0)
    t
  }

  /** Good suffix table: precomputed shift distances based on suffix matches
    */
  private[this] val offsetTable = {
    val t = new Array[Int](needle.length)

    @tailrec def isPrefix(i: Int, j: Int): Boolean =
      i == needle.length || (needle(i) == needle(j) && isPrefix(i + 1, j + 1))

    @tailrec def loop1(i: Int, lastPref: Int): Unit =
      if (i >= 0) {
        val next = if (isPrefix(i + 1, 0)) i + 1 else lastPref
        t(nl1 - i) = next - i + nl1
        loop1(i - 1, next)
      }
    loop1(nl1, needle.length)

    @tailrec def suffixLength(i: Int, j: Int, acc: Int): Int =
      if (i >= 0 && needle(i) == needle(j)) suffixLength(i - 1, j - 1, acc + 1) else acc

    @tailrec def loop2(i: Int): Unit =
      if (i < nl1) {
        val sl = suffixLength(i, nl1, 0)
        t(sl) = nl1 - i + sl
        loop2(i + 1)
      }
    loop2(0)
    t
  }

  /** Find next index of needle in haystack at or after the given offset.
    *
    * @param haystack The ByteString to search in
    * @param offset The starting position for the search
    * @return The index where the needle starts
    * @throws NotEnoughDataException if the needle is not found in the remaining haystack
    */
  def nextIndex(haystack: ByteString, offset: Int): Int = {
    import ByteStringHelpers.byteAt
    @tailrec def rec(i: Int, j: Int): Int = {
      val b = byteAt(haystack, i)
      if (needle(j) == b) {
        if (j == 0) i
        else rec(i - 1, j - 1)
      } else {
        val jump = math.max(offsetTable(nl1 - j), charTable(b & 0xff))
        rec(i + jump, nl1)
      }
    }
    try rec(offset + nl1, nl1)
    catch { case NotEnoughDataException => throw NotEnoughDataException }
  }
}
