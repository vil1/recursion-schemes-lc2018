package lc2018
package solutions

import matryoshka._
import matryoshka.data.Fix
import matryoshka.implicits._
import matryoshka.patterns.EnvT
import org.apache.avro.Schema
import scalaz._, Scalaz._

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
  * Happy hunting.
  */
object SparkConverter extends GDataInstances {

  def isOfSimpleType[D](data: GData[D]) = data match {
    case GStruct(_) | GArray(_) => true
    case _                      => false
  }

  /**
    * We have a proper way to overcome this problem. There is a `para` scheme that works a little bit like cata.
    * Using para, our algebra will "see" not only the result of its application to the level bellow but also
    * the structure of that level we just processed.
    *
    * To use para, we need a special kind of algebra : a GAlgebra. Given a functor F and a comonad W, Galgebra[W, F, A]
    * is simply a function F[W[A]] => A, so our carrier is simply wrapped in an additional layer.
    *
    * For para's GAlgebra we use (T[F], ?) as our comonad, in other words, our carrier will be paired with the "tree" we
    * processed during the previous step.
    *
    * We will use that to know when we need to "unwrap" the value we had wrapped in a Row at the previous step although we
    * shouldn't have.
    */
  def gDataToRow[D](implicit D: Recursive.Aux[D, GData]): GAlgebra[(D, ?), GData, Row] = {
    case GArray(elems) =>
      val values = elems.map {
        case (previous, current) =>
          if (isOfSimpleType(previous.project))
            current
          else
            current.values.head
      }
      Row(values)

    case GStruct(fields) =>
      val values = fields.map {
        case (k, (previous, value)) =>
          if (isOfSimpleType(previous.project)) {
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

  def fromGDataToSparkRow(row: Fix[GData]): Row =
    row.para[Row](gDataToRow)

}

/**
  * We'll also need Avro to serialize streaming data into Kafka topics.
  *
  * This is just another kind of pain :). We will be using Avro's GenericContainer interface.
  * To build a GenericContainer you need an Avro schema, so we'll have to somehow "zip" the data
  * we want to serialize with its schema (this should remind you of something we already did).
  */
object AvroConverter extends SchemaToAvroAlgebras with GDataInstances {

  import scala.collection.JavaConverters._

  /**
    * A generic schema (of type [[SchemaF]]) with each element
    * labelled with the corresponding `avro.Schema`.
    */
  type SchemaWithAvro[A] = EnvT[Schema, SchemaF, A]

  type DataWithSchema[A] = EnvT[Schema, GData, A]

  case class Incompatibility[D](schema: Schema, data: D)

  /**
    * Avro API is not very typesafe, all values inside GenericRecord are treated as mere Objects.
    * They didn't defined a GenericContainer for storing simple values (like numbers, strings, etc).
    * So we need to define one, for there is no way *we* work on non-types like Any or AnyRef.
    */
  case class SimpleValue(value: Any) extends GenericContainer {
    override def getSchema: Schema = ???
  }

  /**
    * But this is for our convenience only, we still need to feed avro API methods with unwrapped
    * simple values, so don't forget to use this method whenever needed.
    */
  def unwrap(container: GenericContainer): Any = {
    container match {
      case SimpleValue(value) => value
      case value              => value
    }
  }

  def fromGDataToAvro[S, D](schema: S, data: D)(
      implicit S: Birecursive.Aux[S, SchemaF],
      D: Birecursive.Aux[D, GData]): \/[Incompatibility[D], GenericContainer] = {

    val zipWithSchemaAlg: CoalgebraM[\/[Incompatibility[D], ?], DataWithSchema, (S, D)] = {
      case (sch, dat) =>
        (sch.project, dat.project) match {

          case (structF @ StructF(fieldsSchema), GStruct(fields)) =>
            val withSchema = GStruct(
              ListMap(fields.map { case (name, fx) => (name, (fieldsSchema(name), fx)) }.toSeq: _*))
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(structF.embed), withSchema)).right

          case (arrF @ ArrayF(fieldSchema), GArray(elems)) =>
            val withSchema = GArray(elems.map(fx => (fieldSchema, fx)))
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(arrF.embed), withSchema)).right

          case (valueF @ StringF(), GString(value)) =>
            val withSchema = GString[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ IntegerF(), GInteger(value)) =>
            val withSchema = GInteger[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ LongF(), GLong(value)) =>
            val withSchema = GLong[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ BooleanF(), GBoolean(value)) =>
            val withSchema = GBoolean[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ FloatF(), GFloat(value)) =>
            val withSchema = GFloat[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ DoubleF(), GDouble(value)) =>
            val withSchema = GDouble[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (valueF @ DateF(), GDate(value)) =>
            val withSchema = GDate[(S, D)](value)
            EnvT[Schema, GData, (S, D)]((schemaFToAvro(valueF.embed), withSchema)).right

          case (s, d) =>
            Incompatibility(schemaFToAvro(s.embed), d.embed).left
        }
    }
    val alg: AlgebraM[\/[Incompatibility[D], ?], DataWithSchema, GenericContainer] = {
      case EnvT((avroSchema, GStruct(fields))) =>
        val bldrWithFields = fields.foldLeft(new GenericRecordBuilder(avroSchema)) { (recordBuilder, container) =>
          val (name, data) = container
          recordBuilder.set(name, unwrap(data))
        }
        bldrWithFields.build().right

      case EnvT((avroSchema, GArray(elem))) =>
        new GenericData.Array[Any](avroSchema, elem.map(unwrap).asJavaCollection).right

      case EnvT((_, GBoolean(el))) => SimpleValue(el).right
      case EnvT((_, GFloat(el)))   => SimpleValue(el).right
      case EnvT((_, GInteger(el))) => SimpleValue(el).right
      case EnvT((_, GDate(el)))    => SimpleValue(el.getTime).right // c.f. logical types
      case EnvT((_, GLong(el)))    => SimpleValue(el).right
      case EnvT((_, GDouble(el)))  => SimpleValue(el).right
      case EnvT((_, GString(el)))  => SimpleValue(el).right
    }

    (schema, data).hyloM[\/[Incompatibility[D], ?], DataWithSchema, GenericContainer](alg, zipWithSchemaAlg)
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
