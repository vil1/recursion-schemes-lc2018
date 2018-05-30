package solutions

import lc2018.solutions._
import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._
import matryoshka.patterns.EnvT
import org.apache.avro.Schema
import scalaz._
import scalaz.Scalaz._

import scala.language.higherKinds
import org.apache.avro.generic.{GenericContainer, GenericData, GenericRecordBuilder}
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

object AvroConverter extends SchemaToAvroAlgebras {

  import scala.collection.JavaConverters._

  /**
    * A generic schema (of type [[SchemaF]]) with each element
    * labelled with the corresponding `avro.Schema`.
    */
  type SchemaWithAvro[A] = EnvT[Schema, SchemaF, A]

  type DataWithSchema[A] = EnvT[Schema, GData, A]

  case class Incompatibility(schema: Schema, data: Fix[GData])

  case class SimpleValue(value: Any) extends GenericContainer {
    override def getSchema: Schema = ???
  }

  def fromGDataToAvro(schema: Fix[SchemaF], data: Fix[GData]): \/[Incompatibility, GenericContainer] = {

    val zipWithSchemaAlg: CoalgebraM[\/[Incompatibility, ?], DataWithSchema, (Fix[SchemaF], Fix[GData])] = {

      case (structF @ Fix(StructF(fieldsSchema)), Fix(GStruct(fields))) =>
        val withSchema = GStruct(ListMap(fields.map { case (name, fx) => (name, (fieldsSchema(name), fx)) }.toSeq: _*))
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(structF), withSchema))
          .right[Incompatibility]

      case (arrF @ Fix(ArrayF(fieldSchema)), Fix(GArray(elems))) =>
        val withSchema = GArray(elems.map(fx => (fieldSchema, fx)))
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(arrF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(StringF()), Fix(GString(value))) =>
        val withSchema = GString[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(IntegerF()), Fix(GInteger(value))) =>
        val withSchema = GInteger[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(LongF()), Fix(GLong(value))) =>
        val withSchema = GLong[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(BooleanF()), Fix(GBoolean(value))) =>
        val withSchema = GBoolean[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(FloatF()), Fix(GFloat(value))) =>
        val withSchema = GFloat[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(DoubleF()), Fix(GDouble(value))) =>
        val withSchema = GDouble[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (valueF @ Fix(DateF()), Fix(GDate(value))) =>
        val withSchema = GDate[(Fix[SchemaF], Fix[GData])](value)
        EnvT[Schema, GData, (Fix[SchemaF], Fix[GData])]((schemaFToAvro(valueF), withSchema))
          .right[Incompatibility]

      case (s, d) =>
        Incompatibility(schemaFToAvro(s), d).left
    }

    val alg: AlgebraM[\/[Incompatibility, ?], DataWithSchema, GenericContainer] = {
      case EnvT((avroSchema, GStruct(fields))) =>
        val bldrWithFields = fields.foldLeft(new GenericRecordBuilder(avroSchema)) { (recordBuilder, container) =>
          val (name, data) = container
          recordBuilder.set(name, unwrap(data))
        }
        bldrWithFields.build().right[Incompatibility]

      case EnvT((avroSchema, GArray(elem))) =>
        new GenericData.Array[Any](avroSchema, elem.map(unwrap).asJavaCollection)
          .right[Incompatibility]

      case EnvT((_, GBoolean(el))) => SimpleValue(el).right[Incompatibility]
      case EnvT((_, GFloat(el)))   => SimpleValue(el).right[Incompatibility]
      case EnvT((_, GInteger(el))) => SimpleValue(el).right[Incompatibility]
      case EnvT((_, GDate(el)))    => SimpleValue(el.getTime).right[Incompatibility] // c.f. logical types
      case EnvT((_, GLong(el)))    => SimpleValue(el).right[Incompatibility]
      case EnvT((_, GDouble(el)))  => SimpleValue(el).right[Incompatibility]
      case EnvT((_, GString(el)))  => SimpleValue(el).right[Incompatibility]
    }

    (schema, data).hyloM[\/[Incompatibility, ?], DataWithSchema, GenericContainer](alg, zipWithSchemaAlg)
  }

  def unwrap(container: GenericContainer): Any = {
    container match {
      case SimpleValue(value) => value
      case value              => value
    }
  }
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
