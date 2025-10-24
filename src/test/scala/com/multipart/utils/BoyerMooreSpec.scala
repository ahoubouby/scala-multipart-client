package com.multipart.utils

import com.multipart.TestFixtures

import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for BoyerMoore string search algorithm
 */
class BoyerMooreSpec extends AnyWordSpec with Matchers {

  "BoyerMoore" should {

    "find a simple pattern in a string" in {
      val needle   = "boundary".getBytes
      val haystack = ByteString("--boundary\r\n")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 2
    }

    "find pattern at the beginning" in {
      val needle   = "start".getBytes
      val haystack = ByteString("start of text")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 0
    }

    "find pattern at the end" in {
      val needle   = "end".getBytes
      val haystack = ByteString("text at the end")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 12
    }

    "find multipart boundary with CRLF prefix" in {
      val boundary = "----WebKitFormBoundary".getBytes
      val needle   = ("\r\n--" + new String(boundary)).getBytes
      val content  = TestFixtures.simpleFormData()
      val haystack = ByteString(content)
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index should be >= 0
    }

    "throw NotEnoughDataException when pattern not found" in {
      val needle   = "notfound".getBytes
      val haystack = ByteString("some text without the pattern")
      val bm       = new BoyerMoore(needle)

      assertThrows[NotEnoughDataException.type] {
        bm.nextIndex(haystack, 0)
      }
    }

    "throw NotEnoughDataException when searching beyond haystack length" in {
      val needle   = "test".getBytes
      val haystack = ByteString("ab")
      val bm       = new BoyerMoore(needle)

      assertThrows[NotEnoughDataException.type] {
        bm.nextIndex(haystack, 0)
      }
    }

    "find pattern with special characters" in {
      val needle   = "\r\n--".getBytes
      val haystack = ByteString("some text\r\n--boundary")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 9
    }

    "find second occurrence when offset is provided" in {
      val needle   = "abc".getBytes
      val haystack = ByteString("abc def abc ghi")
      val bm       = new BoyerMoore(needle)

      val firstIndex = bm.nextIndex(haystack, 0)
      firstIndex shouldBe 0

      val secondIndex = bm.nextIndex(haystack, firstIndex + 1)
      secondIndex shouldBe 8
    }

    "handle single-character needle" in {
      val needle   = "x".getBytes
      val haystack = ByteString("abcxyz")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 3
    }

    "handle needle same length as haystack" in {
      val needle   = "test".getBytes
      val haystack = ByteString("test")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 0
    }

    "handle repeated characters in pattern" in {
      val needle   = "aaaa".getBytes
      val haystack = ByteString("bbbaaaabbb")
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 3
    }

    "fail with empty needle" in {
      assertThrows[IllegalArgumentException] {
        new BoyerMoore(Array.empty[Byte])
      }
    }

    "work with binary data" in {
      val needle   = Array[Byte](0x00.toByte, 0xff.toByte, 0xaa.toByte)
      val haystack = ByteString(0x11.toByte, 0x22.toByte, 0x00.toByte, 0xff.toByte, 0xaa.toByte, 0x33.toByte)
      val bm       = new BoyerMoore(needle)

      val index = bm.nextIndex(haystack, 0)
      index shouldBe 2
    }
  }

  "ByteStringHelpers" should {

    "get byte at valid offset" in {
      val bs = ByteString("test")
      ByteStringHelpers.byteAt(bs, 0) shouldBe 't'.toByte
      ByteStringHelpers.byteAt(bs, 3) shouldBe 't'.toByte
    }

    "throw NotEnoughDataException for invalid offset" in {
      val bs = ByteString("test")
      assertThrows[NotEnoughDataException.type] {
        ByteStringHelpers.byteAt(bs, 10)
      }
    }

    "get char at valid offset" in {
      val bs = ByteString("test")
      ByteStringHelpers.byteChar(bs, 0) shouldBe 't'
      ByteStringHelpers.byteChar(bs, 1) shouldBe 'e'
    }

    "throw NotEnoughDataException for invalid char offset" in {
      val bs = ByteString("test")
      assertThrows[NotEnoughDataException.type] {
        ByteStringHelpers.byteChar(bs, 10)
      }
    }
  }
}
