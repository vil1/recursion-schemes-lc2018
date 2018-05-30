package lc2018

import jto.validation._
import jto.validation.jsonast._
import matryoshka._
import matryoshka.data._
import org.scalacheck.Arbitrary
import scalaz.Scalaz._
import scalaz._
import GDataInstances

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

  // to be able to traverse
  implicit val ruleApplicativeForScalaz: Applicative[JRule] = new Applicative[JRule] {
    override def point[A](a: => A): JRule[A] = Rule.pure(a)

    override def ap[A, B](fa: => JRule[A])(f: => JRule[A => B]): JRule[B] = fa.ap(f)
  }

  // goal - recurse on schema to generate rules
  def fromSchemaToRules(schema: Fix[SchemaF]): JRule[Fix[GData]] = ???

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

  // goal - first generate a schema and then recurse on it to generate the appropriate data
  // handle number of fields
  // handle max depth to "finish somewhere"
  // master is the schema
  def genSchemaAndData: Gen[(Fix[SchemaF], Fix[GData])] = ???
}
