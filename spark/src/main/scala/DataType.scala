package org.apache.spark.sql.types


final case class Metadata(map: Map[String, Any])
object Metadata {
  def empty: Metadata = new Metadata(Map.empty[String, Any])
}

sealed trait DataType


case object BooleanType extends DataType
case object DateType extends DataType
case object DoubleType extends DataType
case object FloatType extends DataType
case object IntegerType extends DataType
case object LongType extends DataType
case object StringType extends DataType
final case class StructField(name: String, dataType: DataType, nullable: Boolean = true, metadata: Metadata = Metadata.empty)
final case class StructType(fields: Array[StructField]) extends DataType {
  override def toString: String = s"StructType${fields.map(_.toString).mkString("(", ", ", ")")}"

  override def equals(other: Any): Boolean = other match {
    case StructType(otherFields) => (fields zip otherFields).forall{ case (l, r) => l == r}
    case _ => false
  }
}
final case class ArrayType(elementType: DataType, containsNull: Boolean) extends DataType 

