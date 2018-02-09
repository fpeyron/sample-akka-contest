package com.betc.danon.game.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.betc.danon.game.models.PrizeDomain
import com.betc.danon.game.models.PrizeDomain.{Prize, PrizeType}
import com.betc.danon.game.utils.CustomMySqlProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait PrizeRepository extends PrizeTable with GamePrizeTable {

  private[repositories] implicit val database: Database
  private[repositories] implicit val ec: ExecutionContext

  object prize {

    /**
      * Prize
      */

    def create(prize: Prize): Future[Try[Int]] = database.run {
      (prizeTableQuery += prize).asTry
    }

    def update(prize: Prize): Future[Try[Int]] = database.run {
      prizeTableQuery.filter(_.id === prize.id).update(prize).asTry
    }

    def delete(id: UUID): Future[Try[Int]] = database.run {
      prizeTableQuery.filter(_.id === id).delete.asTry
    }

    def getById(id: UUID): Future[Option[Prize]] = database.run {
      prizeTableQuery.filter(_.id === id).result.headOption
    }

    def fetchBy(country_code: Option[String] = None, game_id: Option[UUID] = None): Source[Prize, NotUsed] = Source.fromPublisher(
      database.stream {
        prizeTableQuery
          .filter(row => if (country_code.isDefined) row.country_code === country_code.get else true: Rep[Boolean])
          .filter(row => if (game_id.isDefined) row.id in gamePrizeTableQuery.filter(_.game_id === game_id.get).map(_.prize_id) else true: Rep[Boolean])
          .to[List].result
      })

    /**
      * Schema
      */

    def schemaDropCreate(): Unit = Await.result(schemaDropCreateFuture, Duration.Inf)

    def schemaDropCreateFuture: Future[Unit] = database.run {
      DBIO.seq(
        prizeTableQuery.schema.drop.asTry andThen prizeTableQuery.schema.create
      )
    }

    def schemaCreate(): Unit = Await.result(schemaCreateFuture, Duration.Inf)

    def schemaCreateFuture: Future[Unit] = database.run {
      DBIO.seq(prizeTableQuery.schema.create.asTry)
    }

  }

}

private[repositories] trait PrizeTable {

  protected val prizeTableQuery = TableQuery[PrizeTable]

  class PrizeTable(tag: Tag) extends Table[Prize](tag, "REF_PRIZE") {

    implicit val myTimestampColumnType: JdbcType[Instant] with BaseTypedType[Instant] = MappedColumnType.base[Instant, Timestamp](
      dt => new java.sql.Timestamp(dt.toEpochMilli),
      ts => Instant.ofEpochMilli(ts.getTime)
    )

    implicit val prizeTypeColumnType: JdbcType[PrizeDomain.PrizeType.Value] with BaseTypedType[PrizeDomain.PrizeType.Value] = MappedColumnType.base[PrizeType.Value, String](
      e => e.toString,
      s => PrizeType.withName(s)
    )

    override def * = (id, country_code, `type`, label, title, description, picture, vendor_code, face_value, points) <> (create, extract)

    def id = column[UUID]("id", O.PrimaryKey)

    def country_code = column[String]("country_code", O.Length(2, varying = true))

    def `type` = column[PrizeType.Value]("type", O.Length(10, varying = true))

    def label = column[String]("label", O.Length(255, varying = true))

    def title = column[Option[String]]("title", O.Length(255, varying = true))

    def description = column[Option[String]]("description")

    def picture = column[Option[String]]("picture")

    def vendor_code = column[Option[String]]("vendor_code", O.Length(20, varying = true))

    def face_value = column[Option[Int]]("face_value")

    def points = column[Option[Int]]("points")

    def create(d: (UUID, String, PrizeType.Value, String, Option[String], Option[String], Option[String], Option[String], Option[Int], Option[Int])) =
      Prize(id = d._1, countryCode = d._2, `type` = d._3, label = d._4, title = d._5, description = d._6, picture = d._7, vendorCode = d._8, faceValue = d._9, points = d._10)

    def extract(p: Prize) =
      Option(p.id, p.countryCode, p.`type`, p.label, p.title, p.description, p.picture, p.vendorCode, p.faceValue, p.points)
  }

}

