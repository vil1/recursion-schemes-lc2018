package lc2018.solutions

import org.apache.spark.sql.types._
import org.scalacheck._
import org.scalacheck.Prop._
import matryoshka._, implicits._, data.Fix, Fix._
import matryoshka.scalacheck.arbitrary._
import scalaz._, Scalaz._

/**
  * Verifying that our (co)algebras that convert SchemaF from/to DataType are correct should be trivial using
  * property-based testing.
  *
  * But to do that, we first need to be able to generate arbitrary Fix[SchemaF]. So we ned a way to summon instances of
  * Arbitrary[T[SchemaF]] for any fix-point T. We'll again need help from our new friend Delay.
  * Now we only need to verify that, given an arbitrary Fix[SchemaF], converting it to a DataType using `schemaFToDataType`
  * and then convert that back to Fix[SchemaF] using `dataTypeToSchemaF` should produce the initial Fix[SchemaF].
  */
object SchemaFToDataTypeAlgebrasSpec extends Properties("Parquet-related algebras") with SchemaFToDataTypeAlgebras {

  import SchemaF._

  property("invertible") = forAll { (schema: Fix[SchemaF]) =>
    // We want to convert `schema` to DataType and then back to Fix[SchemaF] using the (co)algebras we've just defined.
    val roundtrip: Fix[SchemaF] = schema.cata(schemaFToDataType).ana[Fix[SchemaF]](dataTypeToSchemaF)
    (roundtrip == schema) :| s"$roundtrip\n==\n$schema"
  }
}
