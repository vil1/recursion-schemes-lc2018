package lc2018

import org.apache.spark.sql.types._
import org.scalacheck._
import matryoshka._, implicits._, scalacheck.arbitrary._
import scala.collection.immutable.ListMap

object SchemaFArbitrary extends SchemaFToDataTypeAlgebras {
  implicit val schemaFDelayArbitrary: Delay[Arbitrary, SchemaF] = new Delay[Arbitrary, SchemaF] {
    def apply[A](A: Arbitrary[A]): Arbitrary[SchemaF[A]] =
      Arbitrary {
        Gen.oneOf(
          Gen.const(BooleanF[A]()),
          Gen.const(DateF[A]()),
          Gen.const(DoubleF[A]()),
          Gen.const(FloatF[A]()),
          Gen.const(IntegerF[A]()),
          Gen.const(LongF[A]()),
          Gen.const(StringF[A]()),
          A.arbitrary.map(a => ArrayF(a)),
          Gen.listOfN(5, Gen.zip(Gen.alphaStr, A.arbitrary)).map(m => StructF(ListMap(m: _*)))
        )
      }
  }

  implicit def dataTypeArbitrary[T[_[_]]](implicit
                                          ev: Birecursive.Aux[T[SchemaF], SchemaF],
                                          ev2: scalaz.Functor[SchemaF]): Arbitrary[DataType] =
    Arbitrary(corecursiveArbitrary[T[SchemaF], SchemaF].arbitrary.map { (t: T[SchemaF]) =>
      t.cata(schemaFToDataType)
    })
}
