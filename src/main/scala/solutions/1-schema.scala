package lc2018.solutions

import scala.collection.immutable.ListMap
import scalaz._, Scalaz._
import matryoshka._, implicits._

sealed trait SchemaF[A]
final case class StructF[A](fields: ListMap[String, A]) extends SchemaF[A]
final case class ArrayF[A](element: A)                  extends SchemaF[A]
final case class BooleanF[A]()                          extends SchemaF[A]
final case class DateF[A]()                             extends SchemaF[A]
final case class DoubleF[A]()                           extends SchemaF[A]
final case class FloatF[A]()                            extends SchemaF[A]
final case class IntegerF[A]()                          extends SchemaF[A]
final case class LongF[A]()                             extends SchemaF[A]
final case class StringF[A]()                           extends SchemaF[A]

object SchemaF {

  implicit val schemaFScalazFunctor: Functor[SchemaF] = new Functor[SchemaF] {
    def map[A, B](fa: SchemaF[A])(f: A => B): SchemaF[B] = fa match {
      case StructF(fields) => StructF(fields.map { case (name, value) => name -> f(value) })
      case ArrayF(elem)    => ArrayF(f(elem))
      case BooleanF()      => BooleanF()
      case DateF()         => DateF()
      case DoubleF()       => DoubleF()
      case FloatF()        => FloatF()
      case IntegerF()      => IntegerF()
      case LongF()         => LongF()
      case StringF()       => StringF()
    }
  }

  implicit val schemaFDelayShow: Delay[Show, SchemaF] = new Delay[Show, SchemaF] {
    def apply[A](showA: Show[A]): Show[SchemaF[A]] = lc2018.TODO
  }

}

trait SchemaFToDataTypeAlgebras {

  import org.apache.spark.sql.types._

  def schemaFToDataType: Algebra[SchemaF, DataType] = {
    case StructF(fields) => StructType(fields.map { case (name, value) => StructField(name, value) }.toArray)
    case ArrayF(elem)    => ArrayType(elem, containsNull = false)
    case BooleanF()      => BooleanType
    case DateF()         => DateType
    case DoubleF()       => DoubleType
    case FloatF()        => FloatType
    case IntegerF()      => IntegerType
    case LongF()         => LongType
    case StringF()       => StringType

  }

  def dataTypeToSchemaF: Coalgebra[SchemaF, DataType] = {
    case StructType(fields) => StructF(ListMap(fields.map(f => f.name -> f.dataType): _*))
    case ArrayType(elem, _) => ArrayF(elem)
    case BooleanType        => BooleanF()
    case DateType           => DateF()
    case DoubleType         => DoubleF()
    case FloatType          => FloatF()
    case IntegerType        => IntegerF()
    case LongType           => LongF()
    case StringType         => StringF()

  }

  implicit val dataTypeSchemaRecursive: Birecursive.Aux[DataType, SchemaF] =
    Birecursive.fromAlgebraIso(schemaFToDataType, dataTypeToSchemaF)
}
