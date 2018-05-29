package solutions

import lc2018.solutions._
import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._
import scalaz._
import scalaz.Scalaz._

import scala.language.higherKinds
import org.apache.avro.generic.GenericContainer
import org.apache.spark.sql.Row

import scala.collection.immutable.ListMap
import scala.language.higherKinds

/**
  * It's time to confront ourselves to the real world of manipulating data with Spark & Avro
  * Two specific pain points we have to tackle are :
  *
  * - Spark's org.apache.spark.sql.Row is basically a wrapper of Array[Any]
  * but we need to handle two specifically different behaviour according to the level of the data :
  * When we're handling Arrays and Structs, no worry we need to output a Row
  * but when we're handling "simple" types, then if it's a top-level value we need to output a Row
  * but if it's not, then the value itself must be written.
  *
  * Exemple :
  *   - Value("b") will be Row("b")
  * but
  *   - Struct(a -> Value("b")) will be Row("b") as well (the Row now representing the outer struct)
  *
  * - For Apache Avro, it's a new kind of pain you'll need to overcome, Avro basically represents all of its data
  * as if, it will be at one point or another generated into Java classes.
  * So every "record" or Struct needs to have a qualified name "unique" otherwise the Avro engine will consider
  * the struct as being the same class.
  * But as it will obviously have different fields - you'll most likely end up with an error.
  *
  * Good hunting.
  */
object SparkConverter {

  def fromGDataToSparkRow(row: Fix[GData]): Row = {
    val gAlgebra: GAlgebra[(Fix[GData], ?), GData, Row] = {
      case GArray(elems) =>
        val values = elems.map {
          case (previous, current) =>
            val prev = previous.unFix
            if (prev.isInstanceOf[GArray[_]] || prev.isInstanceOf[GStruct[_]])
              current
            else
              current.values.head
        }
        Row(values)

      case GStruct(fields) =>
        val values = fields.map { field =>
          val (fx, value) = field._2
          val prev        = fx.unFix
          if (prev.isInstanceOf[GArray[_]] || prev.isInstanceOf[GStruct[_]]) {
            value
          } else {
            value.values.head
          }
        }
        Row(values.toSeq: _*)

      case GBoolean(el) => Row(el)
      case GFloat(el)   => Row(el)
      case GInteger(el) => Row(el)
      case GDate(el)    => Row(el)
      case GLong(el)    => Row(el)
      case GDouble(el)  => Row(el)
      case GString(el)  => Row(el)
    }
    row.para[Row](gAlgebra)
  }
}

object AvroConverter {

  def fromGDataToAvroGenericData(data: Fix[GData]): GenericContainer = { ??? }
}

trait GDataInstances {

  implicit val genericDataFTraverse: Traverse[GData] = new Traverse[GData] {
    override def traverseImpl[G[_], A, B](fa: GData[A])(f: A => G[B])(
        implicit evidence$1: Applicative[G]): G[GData[B]] = fa match {
      case GArray(elems) =>
        Functor[G].map(elems.toList traverse f)(GArray.apply)

      case GStruct(fields) =>
        val (keys, values) = fields.unzip
        Functor[G].map(values.toList traverse f)(v => GStruct(ListMap((keys zip v).toSeq: _*)))

      case GString(value)  => Applicative[G].point(GString[B](value))
      case GLong(value)    => Applicative[G].point(GLong[B](value))
      case GInteger(value) => Applicative[G].point(GInteger[B](value))
      case GDouble(value)  => Applicative[G].point(GDouble[B](value))
      case GFloat(value)   => Applicative[G].point(GFloat[B](value))
      case GDate(value)    => Applicative[G].point(GDate[B](value))
      case GBoolean(value) => Applicative[G].point(GBoolean[B](value))
    }
  }
}
