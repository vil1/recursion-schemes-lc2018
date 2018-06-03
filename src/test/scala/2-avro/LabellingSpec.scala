package lc2018

import org.scalacheck.Properties
import org.scalacheck.Prop._
import matryoshka._, implicits._, data.Fix, Fix._
import matryoshka.scalacheck.arbitrary._
import scalaz._, Scalaz._

class AvroSpec extends Properties("Avro-related algebras") with SchemaToAvroAlgebras {

  import SchemaF._

  property("labelling solution") = forAll { (schema: Fix[SchemaF]) =>
    val avro = schemaFToAvro(schema)
    val back = avro.anaM[Fix[SchemaF]](avroToSchemaF)
    (Some(schema) == back) :| s"Some($schema) == $back"
  }

  property("registry solution") = forAll { (schema: Fix[SchemaF]) =>
    val avro = toAvro(schema)
    val back = avro.anaM[Fix[SchemaF]](avroToSchemaF)
    (Some(schema) == back) :| s"Some($schema) == $back"
  }
}
