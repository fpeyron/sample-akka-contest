package fr.sysf.sample.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.event.{Logging, LoggingAdapter}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode}
import akka.{Done, NotUsed}
import fr.sysf.sample.Main.system
import fr.sysf.sample.CustomMySqlProfile.api._
import fr.sysf.sample.models.InstantwinDomain.Instantwin
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

trait InstantwinRepository extends InstantwinTable {

  private[repositories] implicit val database: Database
  implicit val ec: ExecutionContext
  implicit val materializer: ActorMaterializer

  object instantwin {

    //noinspection FieldFromDelayedInit
    val logger: LoggingAdapter = Logging(system.eventStream, getClass)

    /**
      * Instantwin
      */

    def deleteBy(game_id: UUID, gameprize_id: Option[UUID] = None): Future[Try[Int]] = database.run {
      instantwinTableQuery
        .filter(row => row.game_id === game_id)
        .filter(row => if (gameprize_id.isDefined) row.gameprize_id === gameprize_id.get else true: Rep[Boolean])
        .delete.asTry
    }

    def insertAsStream(source: Source[Instantwin, NotUsed]): Future[Done] = source
      .buffer(1000, OverflowStrategy.backpressure)
      .via(Flow[Instantwin].grouped(200))
      .throttle(500, 200.millisecond, 1, ThrottleMode.shaping)
      .runWith(Sink.foreach[Seq[Instantwin]] { i: Seq[Instantwin] =>
        database.run((instantwinTableQuery ++= i).asTry.map {
              case Success(_) =>
              case Failure(e) => logger.error(s"SQL Error, ${e.getMessage}"); throw e
          })
      })

    def fetchBy(game_id: UUID, gameprize_id: Option[UUID] = None): Source[Instantwin, NotUsed] = Source.fromPublisher(
      database.stream {
        instantwinTableQuery
          .filter(row => row.game_id === game_id)
          .filter(row => if (gameprize_id.isDefined) row.gameprize_id === gameprize_id.get else true: Rep[Boolean])
          .sortBy(row => row.activate_date.asc)
          .to[List].result
      })


    /**
      * Schema
      */

    def schemaDropCreateFuture: Future[Unit] = database.run {
      DBIO.seq(
        instantwinTableQuery.schema.drop.asTry andThen instantwinTableQuery.schema.create
      )
    }

    def schemaDropCreate(): Unit = Await.result(schemaDropCreateFuture, Duration.Inf)

    def schemaCreateFuture: Future[Unit] = database.run {
      instantwinTableQuery.schema.create
    }

    def schemaCreate(): Unit = Await.result(schemaCreateFuture, Duration.Inf)

  }

}

private[repositories] trait InstantwinTable {

  class InstantwinTable(tag: Tag) extends Table[Instantwin](tag, "REF_INSTANTWIN") {

    implicit val myTimestampColumnType: JdbcType[Instant] with BaseTypedType[Instant] = MappedColumnType.base[Instant, Timestamp](
      dt => new java.sql.Timestamp(dt.toEpochMilli),
      ts => Instant.ofEpochMilli(ts.getTime)
    )


    def id = column[UUID]("id", O.PrimaryKey)

    def game_id = column[UUID]("game_id")

    def gameprize_id = column[UUID]("gameprize_id")

    def prize_id = column[UUID]("prize_id")

    def activate_date = column[Timestamp]("activate_date")

    override def * = (id, game_id, gameprize_id, prize_id, activate_date) <> (create, extract)


    def create(d: (UUID, UUID, UUID, UUID, Timestamp)) = Instantwin(id = d._1, game_id = d._2, gameprize_id = d._3, prize_id = d._4, activate_date = Instant.ofEpochMilli(d._5.getTime))

    def extract(p: Instantwin) = Option(p.id, p.game_id, p.gameprize_id, p.prize_id, Timestamp.from(p.activate_date))
  }

  protected val instantwinTableQuery = TableQuery[InstantwinTable]
}

