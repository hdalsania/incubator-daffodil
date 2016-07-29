/* Copyright (c) 2016 Tresys Technology, LLC. All rights reserved.
 *
 * Developed by: Tresys Technology, LLC
 *               http://www.tresys.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal with
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimers.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimers in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. Neither the names of Tresys Technology, nor the names of its contributors
 *     may be used to endorse or promote products derived from this Software
 *     without specific prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE
 * SOFTWARE.
 */

package edu.illinois.ncsa.daffodil.processors.unparsers

import edu.illinois.ncsa.daffodil.processors.DINode
import edu.illinois.ncsa.daffodil.processors.InfosetItem
import edu.illinois.ncsa.daffodil.processors.DIArray
import edu.illinois.ncsa.daffodil.processors.DIComplex
import edu.illinois.ncsa.daffodil.processors.DISimple
import edu.illinois.ncsa.daffodil.processors.ElementRuntimeData
import edu.illinois.ncsa.daffodil.processors.ProcessingError
import edu.illinois.ncsa.daffodil.util.Maybe._
import edu.illinois.ncsa.daffodil.processors.DIElement
import edu.illinois.ncsa.daffodil.util.Cursor
import edu.illinois.ncsa.daffodil.util.Accessor
import edu.illinois.ncsa.daffodil.xml._
import edu.illinois.ncsa.daffodil.util.Misc
import edu.illinois.ncsa.daffodil.exceptions.Assert

class InfosetError(kind: String, args: String*) extends ProcessingError("Infoset Error", Nope, Nope, kind, args: _*)

object InfosetCursor {

  def fromInfosetTree(infoset: InfosetItem): InfosetCursor = {
    val is = new InfosetCursorFromTree(infoset)
    is
  }

  def fromXMLNode(xmlDocument: scala.xml.Node, erd: ElementRuntimeData): InfosetCursor = {
    val xmlEventCursor = XMLUtils.nodeToXMLEventCursor(xmlDocument)
    val is = fromXMLEventCursor(xmlEventCursor, erd)
    is
  }

  /**
   * Without constructing the intermediate XML Tree, that is, on the fly, generates the
   * DFDL Infoset events for the InfosetCursor as they are pulled.
   *
   * This is schema aware as it must infer when arrays are starting and ending
   * in order to generate those events as well as the startElement endElement events.
   */
  def fromXMLEventCursor(xmlEventCursor: XMLEventCursor, rootElementERD: ElementRuntimeData): InfosetCursor = {
    val orig = new InfosetCursorFromXMLEventCursor(xmlEventCursor, rootElementERD)
    orig
  }

}

// TODO: Rename to InfosetCursor (and rename file too)
trait InfosetCursor extends Cursor[InfosetAccessor] {

  /**
   * Override these further only if you want to say, delegate from one cursor implemtation
   * to another one.
   */
  override lazy val advanceAccessor = InfosetAccessor(null, null)
  override lazy val inspectAccessor = InfosetAccessor(null, null)
}

object NonUsableInfosetCursor extends InfosetCursor {
  private def doNotUse = Assert.usageError("Not to be called on " + Misc.getNameFromClass(this))
  override lazy val advanceAccessor = doNotUse
  override lazy val inspectAccessor = doNotUse
  override def advance = doNotUse
  override def inspect = doNotUse
  override def fini = doNotUse
}

// TODO - Performance - It's silly to use both a StartKind and EndKind accessor when
// delivering a simple type node. Separate start/end for XML makes sense because there
// are separate events for the contents between those tags, which can be widely separated.
// In Daffodil, the two events will always be back-to-back.
//
// Consider adding a SimpleKind used for simple type elements.
//
sealed trait InfosetEventKind
case object StartKind extends InfosetEventKind { override def toString = "start" }
case object EndKind extends InfosetEventKind { override def toString = "end" }
// case object SimpleKind extends InfosetEventKind

/**
 * An infoset event accessor.
 *
 * Not a case class because we don't want pattern matching on this. (It allocates to pattern match)
 */
class InfosetAccessor private (var kind: InfosetEventKind, var node: DINode) extends Accessor[InfosetAccessor] {
  def namedQName = node.namedQName
  def erd: ElementRuntimeData = node match {
    case a: DIArray => a.parent.runtimeData
    case e: DIElement => e.runtimeData
  }

  override def toString = {
    val evLbl = if (kind eq null) "NullKind" else kind.toString
    val nodeKind = node match {
      case _: DIArray => "array"
      case _: DIElement => "element"
    }
    nodeKind + " " + evLbl + " event for " + erd.namedQName
  }

  /*
   * Methods to use instead of pattern matching
   */
  def isStart = kind == StartKind
  def isEnd = kind == EndKind
  def isElement = node.isInstanceOf[DIElement]
  def asElement: DIElement = node.asInstanceOf[DIElement]
  def isArray = node.isInstanceOf[DIArray]
  def asArray: DIArray = node.asInstanceOf[DIArray]
  def isComplex = node.isInstanceOf[DIComplex]
  def asComplex: DIComplex = node.asInstanceOf[DIComplex]
  def isSimple = node.isInstanceOf[DISimple]
  def asSimple: DISimple = node.asInstanceOf[DISimple]

  override def cpy() = new InfosetAccessor(kind, node)
  override def assignFrom(other: InfosetAccessor) {
    kind = other.kind
    node = other.node
  }
}

object InfosetAccessor {
  def apply(kind: InfosetEventKind, node: DINode) = new InfosetAccessor(kind, node)
}
