package lc2018
package solutions

import matryoshka._, implicits._
import matryoshka.patterns.{ListF, NilF, ConsF}
import scalaz._, Scalaz._

trait DataF[A]
object DataF {
  implicit val dataFunctor: Functor[DataF] = ???
}

final case class Step[S, D](position: Position, schema: S, data: Option[D])
sealed trait EarlyResult
final case class InvalidPath() extends EarlyResult

trait PatchAlgebras {

  type Path = List[Position]

  type Traversal[Schema, Data] = (Path, Schema, Option[Data])

  type ShortCircuitable[A] = EarlyResult \/ A

  def lookupS[S](position: Position, schema: S)(implicit S: Recursive.Aux[S, SchemaF]): Option[S] =
    (position, schema.project) match {
      case (Field(name), StructF(fields)) => fields.get(name)
      case (Index(idx), ArrayF(elem))     => Some(elem)
      case _                              => None
    }

  def lookupD[D](position: Position, data: D)(implicit D: Recursive.Aux[D, DataF]): Option[D] =
    (position, data.project) match {
      case _ => None
    }

  def validatePatch[S, D](
      implicit S: Recursive.Aux[S, SchemaF],
      T: Recursive.Aux[D, DataF]): CoalgebraM[ShortCircuitable, ListF[Step[S, D], ?], Traversal[S, D]] = {
    case (path, schema, data) =>
      if (path.isEmpty) NilF().right
      else
        lookupS(path.head, schema)
          .map { subSchema =>
            val subData = data.flatMap(lookupD(path.head, _))
            ConsF(Step(path.head, schema, data), (path.tail, subSchema, subData)).right
          }
          .getOrElse(InvalidPath().left)
  }

  def applyPatch[S, D](op: Operation): AlgebraM[ShortCircuitable, ListF[Step[S, D], ?], D] = TODO

}
