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

package edu.illinois.ncsa.daffodil.dsom

import edu.illinois.ncsa.daffodil.exceptions.Assert

/**
 * A RealTerm is an element or a sequence or a choice. Group references
 * are excluded as they go away and really have no realization.
 *
 * Things that apply to real terms, but not group references, go in this trait.
 */
trait RealTermMixin { self: Term =>

  final lazy val possibleNextTerms: Seq[Term] = LV('possibleNextTerms) {
    val es = this.nearestEnclosingSequence
    val eus = this.nearestEnclosingUnorderedSequenceBeforeSequence
    val ec = this.nearestEnclosingChoiceBeforeSequence

    val enclosingUnorderedGroup = {
      (ec, eus) match {
        case (None, None) => None
        case (Some(choice), _) => Some(choice)
        case (None, Some(uoSeq)) => Some(uoSeq)
      }
    }
    val listOfNextTerm = (enclosingUnorderedGroup, es) match {
      case (None, None) => Seq.empty
      case (Some(unorderedGroup), _) => {
        // We're in a choice or unordered sequence
        //
        // List must be all of our peers since (as well as our self)
        // we could be followed by any of them plus
        // whatever follows the unordered group.
        val peersCouldBeNext = unorderedGroup.groupMembersNoRefs

        // Note: There was a tiny difference here between the possibleNextTerms of LocalElementBase
        // and the possibleNextTerms of model group. This difference appears to be that this
        // version is more correct (from LocalElementBase).
        val termsUntilFirstRequiredTerm = peersCouldBeNext ++ unorderedGroup.possibleNextTerms
        termsUntilFirstRequiredTerm
      }
      case (None, Some(oSeq)) => {
        // We're in an ordered sequence

        val termsUntilFirstRequiredTerm =
          isDeclaredLastInSequence match {
            case true => oSeq.possibleNextTerms
            case false => {

              val members = oSeq.groupMembersNoRefs

              val nextMember =
                members.dropWhile(m => m != thisTermNoRefs).filterNot(m => m == thisTermNoRefs).headOption

              val nextMembers =
                nextMember match {
                  case Some(e: LocalElementBase) if e.isOptional => Seq(e) ++ e.possibleNextTerms
                  case Some(e: LocalElementBase) => Seq(e)
                  case Some(gb: GroupBase) => Seq(gb.group)
                  case None => Assert.impossibleCase // Difference: model group has Nil here.
                }
              nextMembers
            }
          }
        termsUntilFirstRequiredTerm
      }
    }
    listOfNextTerm
  }.value

  final def isDeclaredLastInSequence = LV('isDeclaredLastInSequence) {
    val es = nearestEnclosingSequence
    // how do we determine what child node we are? We search.
    // TODO: better structure for O(1) answer to this.
    es match {
      case None => Assert.invariantFailed("We are not in a sequence therefore isDeclaredLastInSequence is an invalid question.")
      case Some(s) => {
        val members = s.groupMembersNoRefs
        if (members.last eq thisTermNoRefs) true // we want object identity comparison here, not equality.
        else false
      }
    }
  }.value

  protected def possibleFirstChildTerms: Seq[Term]

  /*
   * Returns list of Elements that could be the first child in the infoset of this model group or element.
   */
  final def possibleFirstChildElementsInInfoset: Seq[ElementBase] = LV('possibleFirstChildElementsInInfoset) {
    val firstChildren = possibleFirstChildTerms.flatMap {
      case e: ElementBase => Seq(e)
      case s: Sequence if s.hiddenGroupRefOption.isDefined => Nil
      case mg: ModelGroup => mg.possibleFirstChildElementsInInfoset
    }
    firstChildren
  }.value

  /*
   * Returns a list of Elements that could follow this Term, including
   * siblings, children of siblings, and siblings of the parent and their children.
   *
   * What stops this is when the end of an enclosing element has to be next.
   */
  final def possibleNextChildElementsInInfoset: Seq[ElementBase] = LV('possibleNextChildElementsInInfoset) {
    val arrayNext = if (isArray) Seq(this.asInstanceOf[ElementBase]) else Nil

    val nextSiblingElements = {
      val poss = possibleNextSiblingTerms
      val res = poss.flatMap {
        possible =>
          possible match {
            case e: ElementBase => Seq(e)
            case mg: ModelGroup => mg.possibleFirstChildElementsInInfoset
          }
      }
      res
    }

    val nextParentElts = nextParentElements
    val res = arrayNext ++ nextSiblingElements ++ nextParentElts
    res
  }.value

  def nextParentElements: Seq[ElementBase]

  protected def couldBeLastElementInModelGroup: Boolean

  /*
   * Returns a list of sibling Terms that could follow this term. This will not
   * return any children of sibling Terms, or any siblings of the parent.
   */
  final def possibleNextSiblingTerms: Seq[Term] = LV('possibleNextSiblingTerms) {
    val listOfNextTerm = enclosingTerm match {
      case None => Nil // root element, has no siblings
      case Some(e: ElementBase) => Nil // complex element, cannot have another model group other than this one
      case Some(c: Choice) => Nil // in choice, no other siblings could come after this one
      case Some(s: Sequence) if !s.isOrdered => s.groupMembersNoRefs // unorderd sequence, all siblings (and myself) could be next
      case Some(s: Sequence) => {
        // in a sequence, the next term could be any later sibling that is not
        // or does not have a required element, up to and including the first
        // term that is/has a required element
        //        def isOutputValueCalc(term: Term) =
        //          term match { case eb: ElementBase if eb.isOutputValueCalc => true; case _ => false }
        val allNextSiblings = s.groupMembersNoRefs.dropWhile(_ != thisTermNoRefs).tail
        val nextSiblings = allNextSiblings // .dropWhile(isOutputValueCalc(_))
        val (optional, firstRequiredAndLater) = nextSiblings.span {
          case e: ElementBase => e.canBeAbsentFromUnparseInfoset
          case mg: ModelGroup => !mg.mustHaveRequiredElement
        }
        optional ++ firstRequiredAndLater.take(1)
      }
    }
    listOfNextTerm
  }.value

}
