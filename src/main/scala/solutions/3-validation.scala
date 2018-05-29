package lc2018.solutions

import scalaz._
import Scalaz._
import matryoshka._
import data._
import implicits._
import jto.validation._
import jto.validation.jsonast._

import scala.collection.immutable.ListMap
import scala.language.higherKinds

/**
  * Now that we have a Schema we will need to validate incoming data (JSON)
  * and output "validated" data or "errors" with what went wrong for the sources
  * to be able to fix their exports.
  *
  * For that we'll use the JTO Validation library but first we need to define what a "Data" is
  */
sealed trait GData[A]
final case class GStruct[A](fields: ListMap[String, A]) extends GData[A]
final case class GArray[A](element: Seq[A])             extends GData[A]
final case class GBoolean[A](value: Boolean)            extends GData[A]
final case class GDate[A](value: java.util.Date)        extends GData[A]
final case class GDouble[A](value: Double)              extends GData[A]
final case class GFloat[A](value: Float)                extends GData[A]
final case class GInteger[A](value: Int)                extends GData[A]
final case class GLong[A](value: Long)                  extends GData[A]
final case class GString[A](value: String)              extends GData[A]

object SchemaRules {
  type JRule[A] = Rule[JValue, A]

  import Rule._
  import shims._

  implicit val app: Applicative[JRule] = applicativeRule

  // CoRecursive.Aux[T[DataF], DataF] - DataF.embed
  //def fromSchemaToRules[T[_[_]], A](schema: SchemaF[A]): Rule[JsValue, T[GData]] = {
  def fromSchemaToRules(schema: Fix[SchemaF]): JRule[Fix[GData]] = {
    val alg: Algebra[SchemaF, JRule[Fix[GData]]] = {
      case StructF(fields) =>
        fields.toList
          .traverse[JRule[?], Fix[GData]] {
            case (name, validation) =>
              (Path \ name).read(_ => validation)
          }
          .map(fs => Fix(GStruct(fs.toMap)))

      case ArrayF(elem) => Rules.pickSeq(elem).map(elems => Fix[GData](GArray(elems)))
      case BooleanF()   => Rules.booleanR.map(x => Fix[GData](GBoolean(x)))
      case DateF()      => Rules.stringR.andThen(Rules.isoDateR).map(x => Fix[GData](GDate(x)))
      case DoubleF()    => Rules.doubleR.map(x => Fix[GData](GDouble(x)))
      case FloatF()     => Rules.floatR.map(x => Fix[GData](GFloat(x)))
      case IntegerF()   => Rules.intR.map(x => Fix[GData](GInteger(x)))
      case LongF()      => Rules.longR.map(x => Fix[GData](GLong(x)))
      case StringF()    => Rules.stringR.map(x => Fix[GData](GString(x)))
    }

    Fix.birecursiveT.cataT(schema)(alg)
  }
}

/**
  * We need to test that validation - of course specific unit tests can be done
  * but we're quite paranoid so let's "generate" abitrary schemas using ScalaCheck
  *
  * But then again - from a Schema we'll be able to generate Rules
  * But to validate those rules we'd need data.
  * So let's generate Data as well :
  * Data that will, of course, need to be compatible with the Schema itself.
  */
trait DataWithSchemaGenerator {
  import org.scalacheck.Gen

  val schemaToDataGen: Algebra[SchemaF, (String, Fix[GData])] = ???

  val genSchemaF: Gen[Fix[SchemaF]] = ???

  def genBooleanF: Gen[Fix[BooleanF]] = ???

  val genSchemaAndData: Gen[(Fix[SchemaF], Fix[GData])] =
    for {
      schemaF       <- genSchemaF
      (name, dataF) = Fix.birecursiveT.cataT(schemaF)(schemaToDataGen)
    } yield (schemaF, dataF)

}
