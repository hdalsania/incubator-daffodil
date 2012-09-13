package daffodil.processors
import java.io.InputStream
import java.nio.channels.ReadableByteChannel

import scala.collection.immutable.PagedSeq
import scala.collection.mutable.HashMap

import daffodil.api.DFDL

/**
 * Pure functional Reader[Byte] that gets its data from a DFDL.Input (aka a ReadableByteChannel)
 */
class DFDLByteReader private (psb : PagedSeq[Byte], val bytePos : Int = 0)
  extends scala.util.parsing.input.Reader[Byte] {

  def this(in : DFDL.Input) = this(PagedSeq.fromIterator(new IterableReadableByteChannel(in)), 0)

  lazy val first : Byte = psb(bytePos)

  lazy val rest : DFDLByteReader = new DFDLByteReader(psb, bytePos + 1)

  lazy val pos : scala.util.parsing.input.Position = new DFDLBytePosition(bytePos)

  lazy val atEnd : Boolean = !psb.isDefinedAt(bytePos)

  /**
   * Factory for a Reader[Char] that constructs characters by decoding them from this
   * Reader[Byte] for a specific encoding starting at a particular byte position.
   */
  def charReader(csName : String) : scala.util.parsing.input.Reader[Char] = {
    DFDLByteReader.getCharReader(psb, bytePos, csName)// new DFDLCharReader(psb, bytePos, csName)
  }  
 
}

/**
 * Reader[Char] constructed from a specific point within a PagedSeq[Byte], for
 * a particular character set encoding. Ends if there is any error trying to decode a
 * character.
 */
class DFDLCharReader private (psc : PagedSeq[Char], charPos : Int)
  extends scala.util.parsing.input.Reader[Char] {

  def this(psb : PagedSeq[Byte], bytePos : Int, csName : String) =
    this({
      val is = new IteratorInputStream(psb.iterator)
      val cs = java.nio.charset.Charset.forName(csName)
      val codec = scala.io.Codec.charset2codec(cs)
      val psc = PagedSeq.fromSource(scala.io.Source.fromInputStream(is)(codec))
      psc
    }, 0)

  lazy val first : Char = psc(charPos)
  lazy val rest : scala.util.parsing.input.Reader[Char] = 
    new DFDLCharReader(psc, charPos + 1)
  lazy val atEnd : Boolean = psc.isDefinedAt(charPos)
  lazy val pos : scala.util.parsing.input.Position = new DFDLCharPosition(charPos)
  
  // def isDefinedAt(charPos : Int) : Boolean = psc.isDefinedAt(charPos)

}

// Scala Reader stuff is not consistent about whether it is generic over the element type, 
// or specific to Char. We want to have a Reader like abstraction that is over bytes, but 
// be able to create real Reader[Char] from it at any byte position.

/**
 * All this excess buffering layer for lack of a way to convert a ReadableByteChannel directly into
 * a PagedSeq. We need an Iterator[Byte] first to construct a PagedSeq[Byte].
 */
class IterableReadableByteChannel(rbc : ReadableByteChannel)
  extends scala.collection.Iterator[Byte] {

  private final val bufferSize = 10000
  private var currentBuf : java.nio.ByteBuffer = _
  private var sz : Int = _

  private def advanceToNextBuf() {
    currentBuf = java.nio.ByteBuffer.allocate(bufferSize)
    sz = rbc.read(currentBuf)
    currentBuf.flip()
  }

  advanceToNextBuf()

  def hasNext() : Boolean = {
    if (sz == -1) return false
    if (currentBuf.hasRemaining()) return true
    advanceToNextBuf()
    if (sz == -1) return false
    if (currentBuf.hasRemaining()) return true
    return false
  }

  var pos : Int = 0

  def next() : Byte = {
    if (!hasNext()) throw new IndexOutOfBoundsException(pos.toString)
    pos += 1
    currentBuf.get()
  }
}

/**
 * Scala's Position is document oriented in that it is 1-based indexing and assumes
 * line numbers and column numbers.
 *
 */
class DFDLBytePosition(i : Int) extends scala.util.parsing.input.Position {
  def line = 1
  def column = i + 1
  // IDEA: could we assume a 'line' of bytes is 32 bytes because those print out nicely as 
  // as in HHHHHHHH HHHHHHHH ... etc. on a 72 character line?
  // Could come in handy perhaps. 
  val lineContents = "" // unused. Maybe this should throw. NoSuchOperation, or something.
}



object DFDLByteReader {
  type PosMap = HashMap[Int, DFDLCharReader]
  type CSMap = HashMap[String, PosMap]
  type PSMap = HashMap[PagedSeq[Byte], CSMap]
  private var charReaderMap : PSMap = HashMap.empty
    
  /**
   * Factory for a Reader[Char] that constructs characters by decoding them from this
   * Reader[Byte] for a specific encoding starting at a particular byte position.
   * 
   * Memoizes so that we don't re-decode as we backtrack around.
   */
  private def getCharReader(psb : PagedSeq[Byte], bytePos : Int, csName : String) : DFDLCharReader = {
    if (charReaderMap.isEmpty) {
      var csMap : CSMap = HashMap.empty
      val emptyCharReaderMap : PosMap = HashMap.empty
      csMap.put(csName, emptyCharReaderMap)
      charReaderMap.put(psb, csMap)
    }
    val charReaders = charReaderMap.get(psb).get.get(csName).get
    charReaders.get(bytePos) match {
      case None => {
    	  val newrdr = new DFDLCharReader(psb, bytePos, csName)
    	  charReaders.put(bytePos, newrdr)
    	  newrdr
      }
      case Some(rdr) => rdr
    }
  }

}

/**
 * Position in a character stream.
 *
 * We ignore line/column structure. It's all one "line" as far as we are concerned.
 */
class DFDLCharPosition(i : Int) extends scala.util.parsing.input.Position {
  def line = 1
  def column = i + 1
  val lineContents = "" // unused
}


/**
 * Whole additional layer of byte-by-byte because there's no way to create 
 * a Source (of Char) from a Seq[Byte]. Instead we have to take our
 * PagedSeq[Byte] to an Iterator, create an InputStream from the Iterator, 
 * and create a Source (of Char) from that.
 * 
 * Convert an iterator of bytes into an InputStream
 */
class IteratorInputStream(ib : Iterator[Byte])
  extends InputStream {

  def read() : Int = 
    if (!ib.hasNext) -1
    else {
      val res = ib.next()
      res
    }
  
  
  val foo : CharSequence = null
}