package fr.sysf.sample.utils

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}
import java.util.UUID

import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

import scala.reflect.ClassTag

/**
  * Contains useful JSON formats: ``j.u.Date``, ``j.u.UUID`` and others; it is useful
  * when creating traits that contain the ``JsonReader`` and ``JsonWriter`` instances
  * for types that contain ``Date``s, ``UUID``s and such like.
  */
trait DefaultJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {

  /**
    * Computes ``RootJsonFormat`` for type ``A`` if ``A`` is object
    */
  def jsonObjectFormat[A: ClassTag]: RootJsonFormat[A] = new RootJsonFormat[A] {
    private val ct = implicitly[ClassTag[A]]

    def write(obj: A): JsValue = JsObject("value" -> JsString(ct.runtimeClass.getSimpleName))

    def read(json: JsValue): A = ct.runtimeClass.newInstance().asInstanceOf[A]
  }

  /**
    * Instance of the ``RootJsonFormat`` for the ``j.u.UUID``
    */
  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {

    override def write(x: UUID) = JsString(x.toString)

    override def read(value: JsValue): UUID = value match {
      case JsString(x) => UUID.fromString(x)
      case x => deserializationError("Expected UUID as JsString, but got " + x)
    }
  }

  /**
    * Instance of the ``RootJsonFormat`` for the ``j.t.Instant``
    */
  implicit object InstantJsonFormat extends RootJsonFormat[Instant] {

    override def write(x: Instant) = JsString(x.toString)

    override def read(jsv: JsValue): Instant = jsv match {
      case JsString(s) if s.endsWith("Z") => Instant.parse(s)
      case JsString(s) => Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s))
      case _ => deserializationError(s"Unknown Instant (ISO 8601 with UTC time zone expected): $jsv")
    }
  }

  /**
    * Instance of the ``RootJsonFormat`` for the ``j.t.LocalDate``
    */
  implicit object LocalDateJsonFormat extends RootJsonFormat[LocalDate] {
    override def write(x: LocalDate) = JsString(x.toString)

    override def read(jsv: JsValue): LocalDate = jsv match {
      case JsString(s) => LocalDate.parse(s)
      //case JsString(s) => LocalDate.from(DateTimeFormatter.ISO_OFFSET_DATE.parse(s))
      case _ => deserializationError(s"Unknown Date (ISO 8601): $jsv")
    }
  }

  implicit def enumFormat[T <: Enumeration](implicit enu: T): RootJsonFormat[T#Value] =
    new RootJsonFormat[T#Value] {
      def write(obj: T#Value): JsValue = JsString(obj.toString)

      def read(json: JsValue): T#Value = {
        json match {
          case JsString(txt) => enu.withName(txt)
          case somethingElse => throw DeserializationException(s"Expected a value from enum $enu instead of $somethingElse")
        }
      }
    }

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json().withParallelMarshalling(parallelism = 8, unordered = true)
}
