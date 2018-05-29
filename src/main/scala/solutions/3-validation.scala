package lc2018.solutions

import jto.validation._
import jto.validation.jsonast._
import matryoshka._
import matryoshka.data._
import scalaz.Scalaz._
import scalaz._

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

  import Rule.applicativeRule
  import shims._

  implicit val ruleApplicativeForScalaz: Applicative[JRule] = new Applicative[JRule] {
    override def point[A](a: => A): JRule[A] = applicativeRule.point(a)

    override def ap[A, B](fa: => JRule[A])(f: => JRule[A => B]): JRule[B] = applicativeRule.ap(f)(fa)
  }

  def fromSchemaToRules(schema: Fix[SchemaF]): JRule[Fix[GData]] = {
    val alg: Algebra[SchemaF, JRule[Fix[GData]]] = {
      case StructF(fields) =>
        fields.toList
          .traverse[JRule[?], (String, Fix[GData])] {
            case (name, validation) =>
              (Path \ name).read(_ => validation.map(fx => (name, fx)))
          }
          .map(fs => Fix(GStruct(ListMap(fs: _*))))

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

  import scala.collection.JavaConverters._

  def genSchemaAndData: Gen[(Fix[SchemaF], Fix[GData])] =
    for {
      schemaF <- genSchemaF
      dataF   <- Fix.birecursiveT.cataT(schemaF)(schemaToDataGen)
    } yield (schemaF, dataF)

  def schemaToDataGen: Algebra[SchemaF, Gen[Fix[GData]]] = {
    case ArrayF(elems) =>
      Gen.listOf(elems).map(lst => Fix[GData](GArray(lst)))

    case StructF(fields) =>
      val (names, values) = fields.unzip
      Gen.sequence(values).map(fields => Fix[GData](GStruct(ListMap((names zip fields.asScala).toSeq: _*))))

    case BooleanF() =>
      Gen.oneOf(true, false).map(value => Fix[GData](GBoolean(value)))

    case DateF() =>
      Gen.choose(0, Long.MaxValue).map(value => Fix[GData](GDate(new java.util.Date(value))))

    case DoubleF() =>
      Gen.choose(Double.MinValue, Double.MaxValue).map(value => Fix[GData](GDouble(value)))

    case FloatF() =>
      Gen.choose(Float.MinValue, Float.MaxValue).map(value => Fix[GData](GFloat(value)))

    case IntegerF() =>
      Gen.choose(Int.MinValue, Int.MaxValue).map(value => Fix[GData](GInteger(value)))

    case LongF() =>
      Gen.choose(Long.MinValue, Long.MaxValue).map(value => Fix[GData](GLong(value)))

    case StringF() =>
      Gen.alphaNumStr.map(value => Fix[GData](GString(value)))
  }

  def genSchemaF: Gen[Fix[SchemaF]] =
    for {
      depth             <- Gen.choose(1, 5)
      nbTopLevelColumns <- Gen.choose(1, 5)
      columns           <- Gen.listOfN(nbTopLevelColumns, genStructSchema(depth))
    } yield Fix[SchemaF](StructF(ListMap(columns: _*)))

  def genValueSchema(): Gen[(String, Fix[SchemaF])] =
    for {
      name <- Gen.identifier
      valueF <- Gen.oneOf[Fix[SchemaF]](
                 Fix[SchemaF](BooleanF()),
                 Fix[SchemaF](DateF()),
                 Fix[SchemaF](DoubleF()),
                 Fix[SchemaF](FloatF()),
                 Fix[SchemaF](IntegerF()),
                 Fix[SchemaF](LongF()),
                 Fix[SchemaF](StringF()),
               )
    } yield (name, valueF)

  def genColumnSchema(maxDepth: Int): Gen[(String, Fix[SchemaF])] =
    if (maxDepth > 0)
      Gen.oneOf[(String, Fix[SchemaF])](genValueSchema(), genStructSchema(maxDepth))
    else genValueSchema()

  def genStructSchema(maxDepth: Int): Gen[(String, Fix[SchemaF])] =
    for {
      name     <- Gen.identifier
      depth    <- Gen.choose(1, maxDepth)
      nbFields <- Gen.choose(0, 3)
      fields   <- Gen.listOfN(nbFields, genColumnSchema(maxDepth - depth))
    } yield (name, Fix[SchemaF](StructF(ListMap(fields: _*))))

  def genArraySchema(maxDepth: Int): Gen[(String, Fix[SchemaF])] =
    for {
      name       <- Gen.identifier
      depth      <- Gen.choose(1, maxDepth)
      (_, elems) <- genNonArraySchema(maxDepth - depth)
    } yield (name, Fix[SchemaF](ArrayF(elems)))

  def genNonArraySchema(maxDepth: Int): Gen[(String, Fix[SchemaF])] =
    if (maxDepth > 0)
      Gen.oneOf[(String, Fix[SchemaF])](genValueSchema(), genStructSchema(maxDepth))
    else genValueSchema()

}
