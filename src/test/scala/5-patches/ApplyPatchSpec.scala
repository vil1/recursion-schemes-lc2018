package lc2018
package solutions

import jto.validation.jsonast._
import org.scalacheck.Properties
import org.scalacheck.Prop._

import matryoshka._, implicits._
import matryoshka.data.Fix, Fix._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.ListMap

import scalaz.\/-

class ApplyPatchSpec extends FlatSpec with Matchers with PatchAlgebras {

  val patch = JsonPatch(Replace, List(Field("foo"), Field("bar"), End), JNumber(42))

  val schema: Fix[SchemaF] = StructF(
    ListMap(
      "foo" -> StructF(
        ListMap(
          "bar" -> IntegerF[Fix[SchemaF]]().embed,
          "baz" -> BooleanF[Fix[SchemaF]]().embed
        )).embed,
      "qux" -> StringF[Fix[SchemaF]]().embed
    )).embed

  val initialData: Fix[GData] = GStruct(
    ListMap(
      "foo" -> GStruct(
        ListMap(
          "bar" -> GInteger[Fix[GData]](1).embed,
          "baz" -> GBoolean[Fix[GData]](true).embed
        )).embed,
      "qux" -> GString[Fix[GData]]("hoay!").embed
    )).embed

  val expected = GStruct(
    ListMap(
      "foo" -> GStruct(
        ListMap(
          "bar" -> GInteger[Fix[GData]](42).embed,
          "baz" -> GBoolean[Fix[GData]](true).embed
        )).embed,
      "qux" -> GString[Fix[GData]]("hoay!").embed
    )).embed
  "Applying a patch" should "update the relevant fields" in {
    applyPatch(schema, patch, initialData) should be(\/-(expected))
  }
}
