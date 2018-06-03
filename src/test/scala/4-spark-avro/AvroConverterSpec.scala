package lc2018

import lc2018.GData._
import matryoshka._, implicits._
import matryoshka.data._, Fix._
import org.apache.avro.generic.GenericData
import org.scalacheck.Prop._
import org.scalacheck._
import org.scalatest.{FlatSpec, Matchers}
import AvroConverter._

import scala.collection.immutable.ListMap

class AvroConverterSpec extends Properties("Avro-rules algebras") with SchemaToAvroAlgebras {

  property("should generate valid avro data") = forAll(genSchemaAndData[Fix[SchemaF], Fix[GData]]) {
    case (schema, data) =>
      val result      = fromGDataToAvro(schema, data)
      val avroSchema  = schemaFToAvro(schema)
      val genericData = GenericData.get()

      result.isRight :| s"Failed to generate avro data" &&
      genericData.validate(avroSchema, result.toOption.get) :| "Datum generated is not valid according to Avro Schema"
  }
}

class AvroConverterTest extends FlatSpec with Matchers {

  "Avro conversion" should "work with sample schema and data" in {
    val body   = """{"a": "toto", "b": 12}"""
    val schema = Fix[SchemaF](StructF(ListMap("a" -> Fix(StringF()), "b" -> Fix(IntegerF()))))
    val data   = Fix[GData](GStruct(ListMap("a" -> Fix(GString("toto")), "b" -> Fix(GInteger(12)))))
    val avro   = fromGDataToAvro(schema, data)

    avro.isRight should be(true)
    val result = avro.toOption.get
    result.toString should be("""{"a": "toto", "b": 12}""")
    GenericData.get().validate(schemaFToAvro(schema), result) should be(true)
  }
}
