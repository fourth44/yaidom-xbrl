/*
 * Copyright 2011 Chris de Vreeze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cdevreeze.yaidom
package xbrl
package integrationtest

import java.io.File

import scala.collection.immutable

import org.junit.Test
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Suite

import ElemApi.withEName
import eu.cdevreeze.yaidom.Document
import eu.cdevreeze.yaidom.xbrl.xbrli.DomElem.IndexedDomElem
import eu.cdevreeze.yaidom.xbrl.xbrli.ItemFact
import eu.cdevreeze.yaidom.xbrl.xbrli.XbrlInstanceDocument

/**
 * BD formula test.
 *
 * @author Chris de Vreeze
 */
@RunWith(classOf[JUnitRunner])
class BdFormulaTest extends Suite {

  private val pathToParentDir: java.io.File =
    (new java.io.File(classOf[BdFormulaTest].getResource("IHZ2013-01.xbrl").toURI)).getParentFile

  private val clazz = classOf[BdFormulaTest]

  /**
   * Tests the equivalent of formula Saldo_fiscale_winstberekening__volgens_vermogensvergelijking__Regel_3_173.xml.
   */
  @Test def testXbrlProcessing(): Unit = {
    // Using a yaidom DocumentParser that used SAX internally
    val docParser = parse.DocumentParserUsingSax.newInstance

    val parentDir = new File(pathToParentDir.getPath)

    val doc: Document =
      docParser.parse(new File(parentDir, "IHZ2013-01.xbrl"))

    // Edit the document, updating bd-bedr:BalanceProfitCalculationForTaxPurposesFiscal with contextRef c1
    val paths = doc.documentElement.filterElemPaths(_.qname == QName("bd-bedr:BalanceProfitCalculationForTaxPurposesFiscal"))
    val editedDoc = doc.updatedAtPaths(paths.toSet) {
      case (elem, path) =>
        elem.plusAttribute(QName("contextRef"), "c1")
    }

    val xbrlInstanceDoc: XbrlInstanceDocument = new XbrlInstanceDocument(editedDoc.uriOption, indexed.Elem(editedDoc.documentElement))

    import ElemApi._

    require {
      xbrlInstanceDoc.xbrlInstance.allTopLevelItems.size >= 20
    }

    require(xbrlInstanceDoc.wrappedElem.toElem.findAllElemsOrSelf.map(_.scope).toSet.size == 1)

    val scope = xbrlInstanceDoc.wrappedElem.scope
    import scope._

    // The "value assertion" itself

    val saldoVolgensVermogensVergelijkingFacts: immutable.IndexedSeq[ItemFact] = {
      val factsFilteredOnName =
        xbrlInstanceDoc.xbrlInstance.filterItems(withEName(QName("bd-bedr:BalanceProfitComparisonMethod").res))

      factsFilteredOnName filter { fact =>
        val context = xbrlInstanceDoc.xbrlInstance.allContextsById(fact.contextRef)
        context.filterElems(withEName(QName("xbrldi:explicitMember").res)) exists { elem =>
          (elem.wrappedElem.toElem.attributeAsResolvedQName(EName("dimension")) == QName("bd-dim-dim:PartyDimension").res) &&
            (elem.wrappedElem.toElem.textAsResolvedQName == QName("bd-dim-dom:Declarant").res)
        }
      }
    }

    val saldoFacts: immutable.IndexedSeq[ItemFact] = {
      val factsFilteredOnName =
        xbrlInstanceDoc.xbrlInstance.filterItems(withEName(QName("bd-bedr:BalanceProfitCalculationForTaxPurposesFiscal").res))

      factsFilteredOnName filter { fact =>
        val context = xbrlInstanceDoc.xbrlInstance.allContextsById(fact.contextRef)
        context.filterElems(withEName(QName("xbrldi:explicitMember").res)) exists { elem =>
          (elem.wrappedElem.toElem.attributeAsResolvedQName(EName("dimension")) == QName("bd-dim-dim:PartyDimension").res) &&
            (elem.wrappedElem.toElem.textAsResolvedQName == QName("bd-dim-dom:Declarant").res)
        }
      }
    }

    val varSetEvals =
      for {
        saldoVolgensVermogensVergelijking <- saldoVolgensVermogensVergelijkingFacts
        saldo <- saldoFacts
        // Very naive approximation of implicit filtering (matching on uncovered aspects)
        if saldoVolgensVermogensVergelijking.contextRef == saldo.contextRef
      } yield {
        // The value assertion test
        BigDecimal(saldoVolgensVermogensVergelijking.text.trim) == BigDecimal(saldo.text.trim)
      }

    assertResult(1) {
      varSetEvals.size
    }

    assertResult(true) {
      varSetEvals forall (_ == true)
    }
  }

  // TODO Totaal_vermogensverschil_Regel_1_203.xml
  // TODO Belastbare_winst_Regel_2_31.xml
  // TODO Identificatienummer_Regel_1_1723.xml
}
