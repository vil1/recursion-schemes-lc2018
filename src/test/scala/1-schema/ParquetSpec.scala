package lc2018

import org.apache.spark.sql.types._
import org.scalacheck._
import org.scalacheck.Prop._
import matryoshka._, implicits._, data.Fix, Fix._, scalacheck.arbitrary._
import scalaz._, Scalaz._

object SchemaFToDataTypeAlgebrasSpec extends Properties("Parquet-related algebras") with SchemaFToDataTypeAlgebras {

  import SchemaFArbitrary._
  import SchemaF._

  implicitly[Arbitrary[DataType]]

  property("invertible") = forAll { (dataType: DataType) =>
    val res = dataType.hylo(schemaFToDataType, dataTypeToSchemaF)
    (res == dataType) :| s"$res\n==\n$dataType"
  }
}
