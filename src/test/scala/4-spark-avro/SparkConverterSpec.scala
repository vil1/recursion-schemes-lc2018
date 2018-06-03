package lc2018

import lc2018.GData._
import lc2018.SparkConverter._
import matryoshka._
import matryoshka.implicits._
import matryoshka.data.Fix._
import matryoshka.data._
import org.apache.spark.sql.Row
import org.scalacheck.Prop._
import org.scalacheck._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.ListMap

class SparkConverterSpec extends Properties("Spark-rules algebras") with SchemaToAvroAlgebras {

  property("should generate valid and compatible data vs schema") = forAll(genSchemaAndData[Fix[SchemaF], Fix[GData]]) {
    case (_, data) => fromGDataToSparkRow(data) != null // should not fail
  }
}

class SparkConverterTest extends FlatSpec with Matchers {

  "Spark conversions" should "work with sample schema and data" in {
    val body = """{"a": "toto", "b": 12}"""
    val data = Fix[GData](GStruct(ListMap("a" -> Fix(GString("toto")), "b" -> Fix(GInteger(12)))))
    val row  = fromGDataToSparkRow(data)
    row should be(Row("toto", 12))
  }

  it should "work with nested data as well" in {
    val body = """{"a": "toto", "b": { "c": 12 }}"""
    val data = Fix[GData](
      GStruct(
        ListMap(
          "a" -> Fix(GString("toto")),
          "b" -> Fix(GStruct(ListMap("c" -> Fix(GInteger(12)))))
        )))
    val row = fromGDataToSparkRow(data)
    row should be(Row("toto", Row(12)))
  }
}
