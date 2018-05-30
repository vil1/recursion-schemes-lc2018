package lc2018.solutions

import jto.validation.jsonast.Ast
import lc2018.solutions.GData._
import lc2018.solutions.SchemaRules._
import matryoshka._
import matryoshka.data._
import matryoshka.implicits._
import org.joda.time.LocalDateTime
import org.joda.time.format.ISODateTimeFormat
import org.scalacheck.Prop._
import org.scalacheck._
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json._
import SchemaF._

import scala.collection.immutable.ListMap

class SchemaRulesSpec extends Properties("Schema-rules algebras") with SchemaToAvroAlgebras {

  property("should generate valid and compatible data vs schema") = forAll(genSchemaAndData) {
    case (schema, data) =>
      val rules       = fromSchemaToRules(schema)
      val jsonPayload = toJson(data)
      val result      = Ast.from.validate(jsonPayload).map(rules.validate)
      result.isValid :| s"Rules did not validate arbitrary data and schema : $schema and $data"
  }

  def toJson(value: Fix[GData]): JsValue = {
    val alg: Algebra[GData, JsValue] = {
      case GStruct(fields) =>
        JsObject(fields)

      case GArray(elems) =>
        JsArray(elems)

      case GBoolean(el) => JsBoolean(el)
      case GFloat(el)   => JsNumber(BigDecimal.decimal(el))
      case GInteger(el) => JsNumber(el)
      case GDate(el)    => JsString(LocalDateTime.fromDateFields(el).toString(ISODateTimeFormat.basicDateTime()))
      case GLong(el)    => JsNumber(el)
      case GDouble(el)  => JsNumber(el)
      case GString(el)  => JsString(el)

    }
    value.cata(alg)
  }
}

class SchemaRulesTest extends FlatSpec with Matchers {

  "Rule generation" should "work with sample schema and data" in {
    val body   = """{"a": "toto", "b": 12}"""
    val schema = Fix[SchemaF](StructF(ListMap("a" -> Fix(StringF()), "b" -> Fix(IntegerF()))))
    val data   = Fix[GData](GStruct(ListMap("a" -> Fix(GString("toto")), "b" -> Fix(GInteger(12)))))
    val rules  = fromSchemaToRules(schema)
    val result = Ast.from.validate(Json.parse(body)).map(rules.validate)
    result.isValid should be(true)
  }
}
