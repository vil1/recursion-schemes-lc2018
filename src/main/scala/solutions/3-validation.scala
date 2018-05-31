package lc2018.solutions

import jto.validation._
import jto.validation.jsonast._
import matryoshka._, implicits._
import matryoshka.data._
import org.scalacheck.Arbitrary
import scalaz.Scalaz._
import scalaz._
import solutions.GDataInstances

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

object GData extends GDataInstances with DataWithSchemaGenerator

object SchemaRules {
  type JRule[A] = Rule[JValue, A]

  implicit val ruleApplicativeForScalaz: Applicative[JRule] = new Applicative[JRule] {
    override def point[A](a: => A): JRule[A] = Rule.pure(a)

    override def ap[A, B](fa: => JRule[A])(f: => JRule[A => B]): JRule[B] = fa.ap(f)
  }

  def fromSchemaToRules[S, D](schema: S)(implicit S: Recursive.Aux[S, SchemaF],
                                         D: Corecursive.Aux[D, GData]): JRule[D] = {
    val alg: Algebra[SchemaF, JRule[D]] = {
      case StructF(fields) =>
        fields.toList
          .traverse[JRule, (String, D)] {
            case (name, validation) =>
              (Path \ name).read(_ => validation.map(fx => (name, fx)))
          }
          .map(fs => GStruct(ListMap(fs: _*)).embed)

      case ArrayF(elem) => Rules.pickSeq(elem).map(elems => GArray(elems).embed)
      case BooleanF()   => Rules.booleanR.map(x => GBoolean[D](x).embed)
      case DateF()      => Rules.stringR.andThen(Rules.isoDateR).map(x => GDate[D](x).embed)
      case DoubleF()    => Rules.doubleR.map(x => GDouble[D](x).embed)
      case FloatF()     => Rules.floatR.map(x => GFloat[D](x).embed)
      case IntegerF()   => Rules.intR.map(x => GInteger[D](x).embed)
      case LongF()      => Rules.longR.map(x => GLong[D](x).embed)
      case StringF()    => Rules.stringR.map(x => GString[D](x).embed)
    }

    schema cata alg
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

  def genSchemaAndData[S, D](implicit S: Birecursive.Aux[S, SchemaF], D: Corecursive.Aux[D, GData]): Gen[(S, D)] =
    for {
      schemaF <- genSchemaF
      dataF   <- schemaF cata schemaToDataGen
    } yield (schemaF, dataF)

  def schemaToDataGen[D](implicit D: Corecursive.Aux[D, GData]): Algebra[SchemaF, Gen[D]] = {
    case ArrayF(elems) =>
      Gen.listOf(elems).map(lst => GArray(lst).embed)

    case StructF(fields) =>
      val (names, values) = fields.unzip
      Gen.sequence(values).map(fields => GStruct(ListMap((names zip fields.asScala).toSeq: _*)).embed)

    case BooleanF() =>
      Gen.oneOf(true, false).map(value => GBoolean[D](value).embed)

    case DateF() =>
      Gen.choose(0, Long.MaxValue).map(value => GDate[D](new java.util.Date(value)).embed)

    case DoubleF() =>
      Gen.choose(Double.MinValue, Double.MaxValue).map(value => GDouble[D](value).embed)

    case FloatF() =>
      Gen.choose(Float.MinValue, Float.MaxValue).map(value => GFloat[D](value).embed)

    case IntegerF() =>
      Gen.choose(Int.MinValue, Int.MaxValue).map(value => GInteger[D](value).embed)

    case LongF() =>
      Gen.choose(Long.MinValue, Long.MaxValue).map(value => GLong[D](value).embed)

    case StringF() =>
      Gen.alphaNumStr.map(value => GString[D](value).embed)
  }

  def genSchemaF[S](implicit S: Corecursive.Aux[S, SchemaF]): Gen[S] =
    for {
      depth             <- Gen.choose(1, 1)
      nbTopLevelColumns <- Gen.choose(1, 1)
      columns           <- Gen.listOfN(nbTopLevelColumns, genStructSchema(depth))
    } yield StructF(ListMap(columns: _*)).embed

  def genValueSchema[S](implicit S: Corecursive.Aux[S, SchemaF]): Gen[(String, S)] =
    for {
      name <- Gen.identifier
      valueF <- Gen.oneOf(
                 BooleanF[S]().embed,
                 DateF[S]().embed,
                 DoubleF[S]().embed,
                 FloatF[S]().embed,
                 IntegerF[S]().embed,
                 LongF[S]().embed,
                 StringF[S]().embed,
               )
    } yield (name, valueF)

  def genColumnSchema[S](maxDepth: Int)(implicit S: Corecursive.Aux[S, SchemaF]): Gen[(String, S)] =
    if (maxDepth > 0)
      Gen.oneOf[(String, S)](genValueSchema, genStructSchema(maxDepth))
    else genValueSchema

  def genStructSchema[S](maxDepth: Int)(implicit S: Corecursive.Aux[S, SchemaF]): Gen[(String, S)] =
    for {
      name     <- Gen.identifier
      depth    <- Gen.choose(1, maxDepth)
      nbFields <- Gen.choose(0, 3)
      fields   <- Gen.listOfN(nbFields, genColumnSchema(maxDepth - depth))
    } yield (name, StructF(ListMap(fields: _*)).embed)

  def genArraySchema[S](maxDepth: Int)(implicit S: Corecursive.Aux[S, SchemaF]): Gen[(String, S)] =
    for {
      name       <- Gen.identifier
      depth      <- Gen.choose(1, maxDepth)
      (_, elems) <- genNonArraySchema(maxDepth - depth)
    } yield (name, ArrayF(elems).embed)

  def genNonArraySchema[S](maxDepth: Int)(implicit S: Corecursive.Aux[S, SchemaF]): Gen[(String, S)] =
    if (maxDepth > 0)
      Gen.oneOf[(String, S)](genValueSchema, genStructSchema(maxDepth))
    else genValueSchema
}
