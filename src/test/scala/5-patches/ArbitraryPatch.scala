package lc2018
package solutions

import jto.validation.jsonast.Ast
import org.scalacheck.{Arbitrary, Gen}
import matryoshka._, implicits._

trait ArbitraryPatch extends GDataInstances with SchemaToAvroAlgebras with DataWithSchemaGenerator {
  def patchForData[S, D](schema: S, data: D)(implicit S: Recursive.Aux[S, SchemaF],
                                             D: Birecursive.Aux[D, GData]): Gen[JsonPatch] =
    for {
      depth       <- Gen.choose(1, 10)
      (path, sch) <- pathInData(depth, schema, data)
      patchValue  <- sch cata schemaToDataGen
    } yield JsonPatch(Replace, path, toJson(patchValue))

  def pathInData[S, D](depth: Int, schema: S, data: D)(implicit S: Recursive.Aux[S, SchemaF],
                                                       D: Recursive.Aux[D, GData]): Gen[(List[Position], S)] =
    if (depth == 0) Gen.const((End :: Nil, schema))
    else {
      (schema.project, data.project) match {
        case (StructF(fieldsS), GStruct(fieldsD)) if fieldsD.nonEmpty =>
          for {
            head        <- Gen.oneOf(fieldsS.keys.toSeq)
            sch         = fieldsS(head)
            (tail, sub) <- pathInData(depth - 1, sch, fieldsD(head))
          } yield (Field(head) :: tail, sub)
        case (ArrayF(elem), GArray(elems)) if elems.nonEmpty =>
          for {
            head        <- Gen.choose(0, elems.size - 1)
            (tail, sub) <- pathInData(depth - 1, elem, elems(head))
          } yield (Index(head) :: tail, sub)
        case _ => (End :: Nil, schema)
      }
    }

  implicit def dataAndTwoPatches[S, D](implicit S: Birecursive.Aux[S, SchemaF],
                                       D: Birecursive.Aux[D, GData]): Arbitrary[(S, D, JsonPatch, JsonPatch)] =
    Arbitrary {
      for {
        (s, data) <- genSchemaAndData[S, D]
        patch1    <- patchForData(s, data)
        patch2    <- patchForData(s, data)
      } yield (s, data, patch1, patch2)
    }
}
