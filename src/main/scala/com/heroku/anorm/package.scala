package com.heroku

import _root_.anorm.MayErr._
import _root_.anorm._
import _root_.anorm.MetaDataItem
import org.postgresql.util.PGobject
import scala.Right
import scala.Left
import java.sql.{Array => SqlArray}

package object anorm {

  val herokuAnorm = "idea please dont optimize away my implicits"

  def enumToType[T](convert: String => Option[T])(implicit m: Manifest[T]): Column[T] = Column.nonNull {
    (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case o: PGobject if convert(o.getValue).isDefined => eitherToError(Right(convert(o.getValue).get)): MayErr[SqlRequestError, T]
        case _ => eitherToError(Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to" + m.erasure.getSimpleName + "for column " + qualified)))
      }
  }

  /*convert a Seq[String,Any] to a 2 dimensional array, for conversion to hstore*/
  implicit val tuple2SeqToStatement = new ToStatement[Seq[(String, Any)]] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: Seq[(String, Any)]): Unit = {
      val toArray = aValue.map(tuple => Array(tuple._1, tuple._2.toString)).toArray
      s.setArray(index, s.getConnection.createArrayOf("varchar", toArray.asInstanceOf[Array[Object]]))
    }
  }

  /*used when using select hstore_to_matrix(hstore_col) from table with hstore*/
  implicit def rowToMap: Column[Map[String, String]] = Column.nonNull {
    (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case a: SqlArray =>
          val fromHstore: Map[String, String] = a.getArray.asInstanceOf[Array[AnyRef]].map {
            twoElems =>
              val twoStrs = twoElems.asInstanceOf[Array[String]]
              twoStrs(0) -> twoStrs(1)
          }.toMap
          eitherToError(Right(fromHstore)): MayErr[SqlRequestError, Map[String, String]]
        case _ => eitherToError(Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Map[String,String] for column " + qualified)))
      }
  }

}