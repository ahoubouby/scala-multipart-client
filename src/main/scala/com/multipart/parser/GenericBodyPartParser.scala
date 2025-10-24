package com.multipart.parser

import java.nio.charset.StandardCharsets
import com.multipart.classifier.{ChainedClassifier, PartClassifier}
import com.multipart.client._
import com.multipart.parser.Part.RawPart
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.stream._
import org.apache.pekko.stream.stage._
import org.apache.pekko.util.ByteString

import scala.annotation.tailrec

// ---------------------------------------------
// Helpers (unchanged public surface)
// ---------------------------------------------
private object ByteStringHelpers {
  def byteAt(input: ByteString, offset: Int): Byte =
    if (offset >= input.length) throw NotEnoughDataException else input(offset)

  def byteChar(input: ByteString, offset: Int): Char = byteAt(input, offset).toChar
}

case object NotEnoughDataException extends Exception("Not enough data") {
  override def fillInStackTrace(): Throwable = this
}

/** Efficient Boyer–Moore search on bytes. Pure and reusable.
  */
private final class BoyerMoore(needle: Array[Byte]) extends LazyLogging {
  require(needle.nonEmpty, "needle must be non-empty")

  private[this] val nl1 = needle.length - 1
  private[this] val charTable = {
    val t = Array.fill(256)(needle.length)
    @tailrec def rec(i: Int): Unit =
      if (i < nl1) { t(needle(i) & 0xff) = nl1 - i; rec(i + 1) }
    rec(0)
    t
  }
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

  /** Find next index of `needle` in `haystack` at/after `offset`. Throws NotEnoughDataException if not enough data. */
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

// ===================================================================================
// Functional core: pure parser state machine
// ===================================================================================

private object Parser {

  // ---- Domain emitted to the shell (what to do next)
  sealed trait Out
  final case class EmitBytes(bs: ByteString) extends Out // Right(bytes)
  final case class EmitPart(part: Part[Unit]) extends Out // Left(part)
  final case class Fail(message: String) extends Out
  final case class BufferExceeded(message: String) extends Out
  case object Terminate extends Out

  // ---- Internal phases
  sealed trait Phase
  case object InitialBoundary extends Phase
  final case class Preamble(searchFrom: Int) extends Phase
  final case class Headers(start: Int, mem: Int) extends Phase
  final case class FileData(offset: Int, mem: Int, name: String) extends Phase
  final case class DataBody(start: Int, mem: Int, name: String) extends Phase
  final case class BadBody(start: Int, mem: Int, headers: Map[String, String]) extends Phase
  case object Done extends Phase

  // ---- Immutable parser state
  final case class State(
    input:       ByteString,
    phase:       Phase,
    terminated:  Boolean,
    partCounter: Int,
    boyerMoore:  BoyerMoore,
    boundary:    Array[Byte],
    boundaryLen: Int,
    maxHeader:   Int,
    maxMem:      Int,
    crlfcrlf:    ByteString,
    classifiers: Seq[PartClassifier], // pluggable
  )

  // ---- Helpers (pure)
  @inline def crlf(bs: ByteString, off: Int): Boolean =
    ByteStringHelpers.byteChar(bs, off) == '\r' && ByteStringHelpers.byteChar(bs, off + 1) == '\n'

  @inline def doubleDash(bs: ByteString, off: Int): Boolean =
    ByteStringHelpers.byteChar(bs, off) == '-' && ByteStringHelpers.byteChar(bs, off + 1) == '-'

  @tailrec def matchesBoundary(bs: ByteString, offset: Int, needle: Array[Byte], ix: Int = 2): Boolean =
    (ix == needle.length) || (ByteStringHelpers.byteAt(bs, offset + ix - 2) == needle(ix)) && matchesBoundary(
      bs,
      offset,
      needle,
      ix + 1,
    )

  private def parseHeaderLines(header: String): Map[String, String] =
    header.linesIterator.foldLeft(Map.empty[String, String]) {
      (acc, line) =>
        val idx = line.indexOf(':')
        if (idx > 0) acc + (line.substring(0, idx).trim.toLowerCase -> line.substring(idx + 1).trim)
        else acc
    }

  private def classify(headers: Map[String, String], classifiers: Seq[PartClassifier]): PartInfo = {
    val chained = new ChainedClassifier(classifiers)
    chained.classify(headers).getOrElse(UnknownPartInfo(headers))
  }

  // ---- One pure step of the state machine
  final case class Step(next: State, outs: List[Out])

  def step(s: State): Step = s.phase match {

    // -- First boundary at start-of-entity (no CRLF before)
    case InitialBoundary =>
      try
        if (matchesBoundary(s.input, 0, s.boundary)) {
          val ix = s.boundaryLen
          if (crlf(s.input, ix)) Step(s.copy(phase = Headers(ix + 2, 0)), Nil)
          else if (doubleDash(s.input, ix)) Step(s.copy(phase = Done, terminated = true), Terminate :: Nil)
          else Step(s.copy(phase = Preamble(0)), Nil)
        } else Step(s.copy(phase = Preamble(0)), Nil)
      catch {
        case NotEnoughDataException =>
          // Need more bytes; keep state, no outputs.
          Step(s, Nil)
      }

    // -- Everything before first boundary (skip)
    case Preamble(from) =>
      try {
        val idx       = s.boyerMoore.nextIndex(s.input, from)
        val needleEnd = idx + s.boundary.length
        if (crlf(s.input, needleEnd)) Step(s.copy(phase = Headers(needleEnd + 2, 0)), Nil)
        else if (doubleDash(s.input, needleEnd)) Step(s.copy(phase = Done, terminated = true), Terminate :: Nil)
        else Step(s.copy(phase = Preamble(needleEnd)), Nil) // false positive, keep searching
      } catch {
        case NotEnoughDataException =>
          Step(s, Nil) // wait for more data
      }

    // -- Parse headers until CRLF CRLF
    case Headers(start, mem) =>
      val idx = s.input.indexOfSlice(s.crlfcrlf, start)
      if (idx == -1) {
        if (s.input.length - start >= s.maxHeader)
          Step(s, BufferExceeded(s"Header length exceeded ${s.maxHeader}") :: Terminate :: Nil)
        else Step(s, Nil) // need more bytes
      } else if (idx - start >= s.maxHeader) {
        Step(s, BufferExceeded(s"Header length exceeded ${s.maxHeader}") :: Terminate :: Nil)
      } else {
        val headerStr = s.input.slice(start, idx).utf8String
        val headers   = parseHeaderLines(headerStr)
        val partStart = idx + 4
        val headersMem = headers.foldLeft(0)(
          (acc, kv) => acc + kv._1.length + kv._2.length,
        )
        val info    = classify(headers, s.classifiers)
        val nextCnt = s.partCounter + 1

        info match {
          case f: FormDataPartInfo if f.filename.isDefined =>
            val name = f.name; val fn = f.filename.get
            Step(
              s.copy(phase = FileData(partStart, mem + headersMem, name), partCounter = nextCnt),
              EmitPart(FilePart(name, fn, f.contentType, ())) :: Nil,
            )

          case f: FormDataPartInfo =>
            Step(s.copy(phase = DataBody(partStart, mem + headersMem, f.name), partCounter = nextCnt), Nil)

          case r: RelatedPartInfo =>
            Step(s.copy(phase = DataBody(partStart, mem + headersMem, r.contentId), partCounter = nextCnt), Nil)

          case u: UnknownPartInfo =>
            Step(s.copy(phase = BadBody(partStart, mem + headersMem, u.headers), partCounter = nextCnt), Nil)
        }
      }

    // -- Stream file bytes chunk-by-chunk until boundary
    case FileData(offset, mem, name) =>
      try {
        val currentEnd = s.boyerMoore.nextIndex(s.input, offset)
        val needleEnd  = currentEnd + s.boundary.length
        val dataLen    = currentEnd - offset
        if (crlf(s.input, needleEnd))
          Step(s.copy(phase = Headers(needleEnd + 2, mem)), EmitBytes(s.input.slice(offset, currentEnd)) :: Nil)
        else if (doubleDash(s.input, needleEnd))
          Step(
            s.copy(phase = Done, terminated = true),
            EmitBytes(s.input.slice(offset, currentEnd)) :: Terminate :: Nil,
          )
        else
          Step(s, Fail("Unexpected boundary in file data") :: Terminate :: Nil)
      } catch {
        case NotEnoughDataException =>
          // Emit what we safely can (keep needle.length+2 for overlap)
          val emitEnd = s.input.length - s.boundary.length - 2
          if (emitEnd > offset) {
            val chunk = s.input.slice(offset, emitEnd)
            Step(s.copy(phase = FileData(emitEnd, mem, name)), EmitBytes(chunk) :: Nil)
          } else Step(s, Nil)
      }

    // -- Buffer data part fully (metadata, JSON, etc.)
    case DataBody(start, mem, name) =>
      try {
        val currentEnd = s.boyerMoore.nextIndex(s.input, start)
        val needleEnd  = currentEnd + s.boundary.length
        val dataLen    = currentEnd - start
        val newMem     = mem + dataLen
        if (newMem > s.maxMem)
          Step(s, BufferExceeded(s"Memory buffer full ($newMem > ${s.maxMem}) on part $name") :: Terminate :: Nil)
        else if (crlf(s.input, needleEnd))
          Step(
            s.copy(phase = Headers(needleEnd + 2, newMem)),
            EmitPart(DataPart(name, s.input.slice(start, currentEnd))) :: Nil,
          )
        else if (doubleDash(s.input, needleEnd))
          Step(
            s.copy(phase = Done, terminated = true),
            EmitPart(DataPart(name, s.input.slice(start, currentEnd))) :: Terminate :: Nil,
          )
        else Step(s, Fail("Unexpected boundary in data part") :: Terminate :: Nil)
      } catch {
        case NotEnoughDataException =>
          val usage = mem + (s.input.length - start - s.boundary.length)
          if (usage > s.maxMem)
            Step(s, BufferExceeded(s"Memory buffer would exceed limit on part $name") :: Terminate :: Nil)
          else Step(s, Nil)
      }

    // -- Unknown/bad part: emit raw bytes as BadPart
    case BadBody(start, mem, headers) =>
      try {
        val currentEnd = s.boyerMoore.nextIndex(s.input, start)
        val needleEnd  = currentEnd + s.boundary.length
        if (crlf(s.input, needleEnd))
          Step(
            s.copy(phase = Headers(needleEnd + 2, mem)),
            EmitPart(BadPart(headers, s.input.slice(start, currentEnd))) :: Nil,
          )
        else if (doubleDash(s.input, needleEnd))
          Step(
            s.copy(phase = Done, terminated = true),
            EmitPart(BadPart(headers, s.input.slice(start, currentEnd))) :: Terminate :: Nil,
          )
        else Step(s, Fail("Unexpected boundary in bad part") :: Terminate :: Nil)
      } catch {
        case NotEnoughDataException => Step(s, Nil)
      }

    case Done =>
      Step(s, Terminate :: Nil)
  }
}

// ===================================================================================
// Imperative shell: GraphStage (Pekko Streams)
// ===================================================================================

/** # GenericBodyPartParser
  *
  * A high-performance, streaming **multipart** parser for Pekko Streams.
  *
  * ## Highlights
  *   - Uses **Boyer–Moore** for boundary search (O(n) practical).
  *   - **Functional core** (`Parser`) with immutable state; the GraphStage only manages back-pressure and emission.
  *   - Supports **multipart/form-data** and **multipart/related/mixed** via **pluggable classifiers** (e.g.,
  *     form/file/json).
  *   - Enforces **maxHeaderSize** and **maxMemoryBufferSize** (streaming for files, buffered for small data parts).
  *
  * ## Outputs Emits `RawPart`:
  *   - `Left(Part[Unit])` events for structural markers (e.g., `FilePart`, `DataPart`, `BadPart`),
  *   - `Right(ByteString)` chunks for **file content** streaming.
  *
  * ## Error Handling
  *   - Emits `MaxMemoryBufferExceeded` / `ParseError` parts before **terminating** cleanly.
  *
  * @param config
  *   Boundary + limits + classifiers. Boundary must be non-empty and must not end with a space.
  */
final class GenericBodyPartParser(config: MultipartParserConfig)
    extends GraphStage[FlowShape[ByteString, RawPart]]
    with LazyLogging {

  import ByteStringHelpers._
  import Parser._

  require(config.boundary.nonEmpty, "'boundary' parameter must be non-empty")
  require(!config.boundary.endsWith(" "), "'boundary' parameter must not end with a space char")

  private val boundaryBytes: Array[Byte] = {
    val head   = Array('\r'.toByte, '\n'.toByte, '-'.toByte, '-'.toByte)
    val tail   = config.boundary.getBytes(StandardCharsets.UTF_8)
    val result = new Array[Byte](head.length + tail.length)
    System.arraycopy(head, 0, result, 0, head.length)
    System.arraycopy(tail, 0, result, head.length, tail.length)
    result
  }

  private val bm       = new BoyerMoore(boundaryBytes)
  private val crlfcrlf = ByteString("\r\n\r\n")

  val in:             Inlet[ByteString]              = Inlet("GenericBodyPartParser.in")
  val out:            Outlet[RawPart]                = Outlet("GenericBodyPartParser.out")
  override val shape: FlowShape[ByteString, RawPart] = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with LazyLogging {

      // Internal buffer and state tracking (imperative shell)
      private var queue    = collection.immutable.Queue.empty[RawPart]
      private var finished = false

      // functional parser state
      private var state = State(
        input       = ByteString.empty,
        phase       = InitialBoundary,
        terminated  = false,
        partCounter = 0,
        boyerMoore  = bm,
        boundary    = boundaryBytes,
        boundaryLen = boundaryBytes.length - 2,
        maxHeader   = config.maxHeaderSize,
        maxMem      = config.maxMemoryBufferSize,
        crlfcrlf    = crlfcrlf,
        classifiers = config.classifiers,
      )

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        val bytes = grab(in)
        state = state.copy(input = state.input ++ bytes) // append to buffer
        drive()
      }

      override def onPull(): Unit =
        if (queue.nonEmpty) {
          val (h, t) = queue.dequeue
          queue = t
          push(out, h)
        } else if (finished) {
          completeStage()
        } else if (!hasBeenPulled(in)) {
          pull(in)
        }

      override def onUpstreamFinish(): Unit = {
        // If upstream finished but we still have buffered data, try to drive one last time.
        if (state.input.nonEmpty && !finished) drive()
        if (queue.isEmpty) completeStage()
      }

      // Drive the functional parser until it needs more bytes or terminates
      private def drive(): Unit = {
        @tailrec def loop(): Unit = {
          val Step(next, outs) = Parser.step(state)
          state = next
          if (outs.nonEmpty) {
            outs.foreach {
              case EmitBytes(bs)       => if (bs.nonEmpty) queue = queue.enqueue(Right(bs))
              case EmitPart(p)         => queue    = queue.enqueue(Left(p))
              case BufferExceeded(msg) => queue    = queue.enqueue(Left(MaxMemoryBufferExceeded(msg)))
              case Fail(msg)           => queue    = queue.enqueue(Left(ParseError(msg)))
              case Terminate           => finished = true
            }
          }

          // Decide whether to continue stepping:
          val canContinue =
            outs.exists {
              case EmitBytes(_) | EmitPart(_) => true
              case _                          => false
            }

          if (canContinue && !finished) loop()
        }

        loop()

        // Emit if someone is pulling
        if (isAvailable(out) && queue.nonEmpty) {
          val (h, t) = queue.dequeue
          queue = t
          push(out, h)
        } else if (!finished && !hasBeenPulled(in)) {
          pull(in)
        } else if (finished && queue.isEmpty) {
          completeStage()
        }
      }
    }
}
