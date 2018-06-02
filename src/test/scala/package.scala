package lc2018

import jto.validation.jsonast._
import matryoshka._, implicits._
import org.joda.time.LocalDateTime

import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import scalaz.Functor

package object solutions {
  def toJson[D](value: D)(implicit D: Recursive.Aux[D, GData], F: Functor[GData]): JValue = {
    val alg: Algebra[GData, JValue] = {
      case GStruct(fields) =>
        JObject(fields)

      case GArray(elems) =>
        JArray(elems)

      case GBoolean(el) => JBoolean(el)
      case GFloat(el)   => JNumber(el)
      case GInteger(el) => JNumber(el)
      case GDate(el)    => JString(LocalDateTime.fromDateFields(el).toString(ISODateTimeFormat.dateTime()))
      case GLong(el)    => JNumber(el)
      case GDouble(el)  => JNumber(el)
      case GString(el)  => JString(el)

    }
    value.cata(alg)
  }
}
