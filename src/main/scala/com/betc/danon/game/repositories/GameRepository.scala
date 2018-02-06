package com.betc.danon.game.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GamePrize, GameStatusType, GameType}
import com.betc.danon.game.utils.ActorUtil
import com.betc.danon.game.utils.CustomMySqlProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

trait GameRepository extends GameTable with GameLimitTable with GamePrizeTable with GameEanTable with GameFreeCodeTable {

  implicit val database: Database
  implicit val ec: ExecutionContext

  object game {

    /**
      * Game
      */
    def create(game: Game): Future[Try[Unit]] = database.run {
      DBIO.seq(
        gameTableQuery += game,
        gameLimitTableQuery ++= game.limits.map(l => (game.id, l)),
        gameEanTableQuery ++= game.input_eans.map(l => (game.id, l)),
        gameFreecodeTableQuery ++= game.input_freecodes.map(l => (game.id, l))
      ).transactionally.asTry
    }


    def update(game: Game): Future[Try[Unit]] = database.run {
      DBIO.seq(
        gameTableQuery.filter(_.id === game.id).update(game),
        gameLimitTableQuery.filter(_.game_id === game.id).delete,
        gameLimitTableQuery ++= game.limits.map(l => (game.id, l)),
        gameEanTableQuery.filter(_.game_id === game.id).delete,
        gameEanTableQuery ++= game.input_eans.map(l => (game.id, l)),
        gameFreecodeTableQuery.filter(_.game_id === game.id).delete,
        gameFreecodeTableQuery ++= game.input_freecodes.map(l => (game.id, l))
      ).transactionally.asTry
    }


    def delete(game_id: UUID): Future[Try[Unit]] = database.run {
      DBIO.seq(
        gamePrizeTableQuery.filter(_.game_id === game_id).delete,
        gameLimitTableQuery.filter(_.game_id === game_id).delete,
        gameEanTableQuery.filter(_.game_id === game_id).delete,
        gameFreecodeTableQuery.filter(_.game_id === game_id).delete,
        gameTableQuery.filter(_.id === game_id).delete
      ).asTry
    }


    def updateStatus(game_id: UUID, status: GameStatusType.Value): Future[Try[Int]] = database.run {
      (for {c <- gameTableQuery if c.id === game_id} yield c.status).update(status.toString).asTry
    }


    def getById(game_id: UUID): Future[Option[Game]] = {

      database.run(gameTableQuery.filter(_.id === game_id).result.headOption)
    }


    def findByIds(ids: Seq[UUID]): Future[Seq[Game]] = {
      database.run(gameTableQuery.filter(_.id inSet ids).to[Seq].result)
    }


    def getExtendedById(game_id: UUID): Future[Option[Game]] = {
      /*
            val query = gameTableQuery.filter(_.id === game_id)
              .joinLeft(gameLimitTableQuery).on(_.id === _.game_id)
              .joinLeft(gamePrizeTableQuery).on(_._1.id === _.game_id)

            println(query.result.statements.headOption)
            database.run(query.result)
              .map(_.groupBy(t => t._1._1)
                .map(t => (
                  t._1,
                  t._2.flatMap(_._2).map(r => GamePrize(id = r._1, prize_id = r._3, start_date = r._4, end_date = r._5, quantity = r._6)),
                  t._2.flatMap(_._1._2).map(_._2)
                ))
                .map(t => t._1.copy(prizes = t._2, limits = t._3))
                .headOption
              )
              */

      val query: DBIO[(Option[Game], Seq[GameLimit], Seq[String], Seq[String], Seq[GamePrize])] = for {
        game <- gameTableQuery.filter(_.id === game_id).result.headOption
        gameLimit <- if (game.isDefined) gameLimitTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gameEans <- if (game.isDefined) gameEanTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gameFreeCodes <- if (game.isDefined) gameFreecodeTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gamePrizes <- if (game.isDefined) gamePrizeTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
      } yield (game, gameLimit, gameEans, gameFreeCodes, gamePrizes)

      database.run(query).map(r => r._1.map(_.copy(limits = r._2, input_eans = r._3, input_freecodes = r._4, prizes = r._5)))
    }


    def findByCode(code: String): Future[Seq[Game]] = database.run {
      gameTableQuery.filter(_.code === code).to[List].result
    }

    def fetchBy(
                 country_code: Option[String] = None,
                 status: Iterable[GameStatusType.Value] = Iterable.empty,
                 types: Iterable[GameType.Value] = Iterable.empty,
                 code: Option[String] = None,
               ): Source[Game, NotUsed] = Source.fromPublisher {
      database.stream {
        val query = gameTableQuery
          .filter(row => (if (types.isEmpty) None else Some(types)).map(s => row.`type` inSet s.map(_.toString)).getOrElse(true: Rep[Boolean]))
          .filter(row => (if (status.isEmpty) None else Some(status)).map(s => row.status inSet s.map(_.toString)).getOrElse(true: Rep[Boolean]))
          .filter(row => if (country_code.isDefined) row.country_code === country_code.get else true: Rep[Boolean])
          .filter(row => if (code.isDefined) row.code === code.get else true: Rep[Boolean])
          .to[List]
        query.result
      }
    }


    /**
      * GamePrize
      */
    def addPrize(game_id: UUID, prize: GamePrize): Future[Unit] = database.run {
      DBIO.seq(
        gamePrizeTableQuery += (game_id, prize)
      )
    }

    def updatePrize(game_id: UUID, prize: GamePrize): Future[Unit] = database.run {
      DBIO.seq(
        gamePrizeTableQuery.filter(_.id === prize.id).update((game_id, prize))
      )
    }

    def removePrize(game_id: UUID, prize_id: UUID): Future[Unit] = database.run {
      DBIO.seq(
        gamePrizeTableQuery.filter(l => l.game_id === game_id && l.id === prize_id).delete
      )
    }


    /**
      * GameEan
      */
    def setEans(game_id: UUID, eans: Seq[String]): Future[Unit] = database.run {
      DBIO.seq(
        gameEanTableQuery.filter(_.game_id === game_id).delete,
        gameEanTableQuery ++= eans.map((game_id, _))
      )
    }

    def addEan(game_id: UUID, ean: String): Future[Int] = database.run {
      gameEanTableQuery += (game_id, ean)
    }

    def removeEan(game_id: UUID, ean: String): Future[Int] = database.run {
      gameEanTableQuery.filter(e => e.game_id === game_id && e.ean === ean).delete
    }


    /**
      * GameFreecode
      */
    def setFreecodes(game_id: UUID, eans: Seq[String]): Future[Unit] = database.run {
      DBIO.seq(
        gameFreecodeTableQuery.filter(_.game_id === game_id).delete,
        gameFreecodeTableQuery ++= eans.map((game_id, _))
      )
    }

    def addFreecode(game_id: UUID, freecode: String): Future[Int] = database.run {
      gameFreecodeTableQuery += (game_id, freecode)
    }

    def removeFreecode(game_id: UUID, freecode: String): Future[Int] = database.run {
      gameFreecodeTableQuery.filter(e => e.game_id === game_id && e.freecode === freecode).delete
    }

    /**
      * Schema
      */

    def schemaDropCreate(): Unit = Await.result(schemaDropCreateFuture, Duration.Inf)

    def schemaDropCreateFuture: Future[Unit] = database.run {
      DBIO.seq(
        gameTableQuery.schema.drop.asTry andThen gameTableQuery.schema.create,
        gameLimitTableQuery.schema.drop.asTry andThen gameLimitTableQuery.schema.create,
        gamePrizeTableQuery.schema.drop.asTry andThen gamePrizeTableQuery.schema.create,
        gameEanTableQuery.schema.drop.asTry andThen gameEanTableQuery.schema.create,
        gameFreecodeTableQuery.schema.drop.asTry andThen gameFreecodeTableQuery.schema.create
      )
    }

    def schemaCreate(): Unit = Await.result(schemaCreateFuture, Duration.Inf)

    def schemaCreateFuture: Future[Unit] = database.run {
      DBIO.seq(
        gameTableQuery.schema.create.asTry,
        gameLimitTableQuery.schema.create.asTry,
        gamePrizeTableQuery.schema.create.asTry,
        gameEanTableQuery.schema.create.asTry,
        gameFreecodeTableQuery.schema.create.asTry
      )
    }

  }

}

private[repositories] trait GameTable {

  protected val gameTableQuery = TableQuery[GameTable]

  class GameTable(tag: Tag) extends Table[Game](tag, "REF_GAME") {

    implicit val myTimestampColumnType: JdbcType[Instant] with BaseTypedType[Instant] = MappedColumnType.base[Instant, Timestamp](
      dt => new java.sql.Timestamp(dt.toEpochMilli),
      ts => Instant.ofEpochMilli(ts.getTime)
    )

    implicit val gameTypeColumnType: JdbcType[GameType.Value] with BaseTypedType[GameType.Value] = MappedColumnType.base[GameType.Value, String](
      e => e.toString,
      s => GameType.withName(s)
    )

    implicit val gameStatusTypeColumnType: JdbcType[GameStatusType.Value] with BaseTypedType[GameStatusType.Value] = MappedColumnType.base[GameStatusType.Value, String](
      e => e.toString,
      s => GameStatusType.withName(s)
    )

    implicit val gameInputTypeColumnType: JdbcType[GameInputType.Value] with BaseTypedType[GameInputType.Value] = MappedColumnType.base[GameInputType.Value, String](
      e => e.toString,
      s => GameInputType.withName(s)
    )

    override def * = (id, `type`, status, code, parents, country_code, title, start_date, timezone, end_date, input_type, input_point, tags) <> (create, extract)

    def id = column[UUID]("id", O.PrimaryKey)

    def `type` = column[String]("type", O.Length(10, varying = true))

    def status = column[String]("status", O.Length(10, varying = true))

    def code = column[String]("code", O.Length(36, varying = true))

    def parents = column[Option[String]]("parents", O.Length(255, varying = true))

    def country_code = column[String]("country_code", O.Length(2, varying = true))

    def title = column[Option[String]]("title", O.Length(255, varying = true))

    def start_date = column[Instant]("start_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    def timezone = column[String]("timezone", O.Length(10, varying = true))

    def end_date = column[Instant]("end_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    def input_type = column[GameInputType.Value]("input_type", O.Length(20, varying = true))

    def input_point = column[Option[Int]]("input_point")

    def tags = column[Option[String]]("tags", O.Length(255, varying = true))

    def create(t: (UUID, String, String, String, Option[String], String, Option[String], Instant, String, Instant, GameInputType.Value, Option[Int], Option[String])) =
      Game(
        id = t._1,
        `type` = GameType.withName(t._2),
        status = GameStatusType.withName(t._3),
        code = t._4,
        parents = t._5.map(_.split(",").flatMap(ActorUtil.string2UUID).toSeq).getOrElse(Seq.empty),
        country_code = t._6,
        title = t._7,
        start_date = t._8,
        timezone = t._9,
        end_date = t._10,
        input_type = t._11,
        input_point = t._12,
        tags = t._13.map(_.split(",").toSeq).getOrElse(Seq.empty)
      )

    def extract(g: Game) = Option(
      g.id,
      g.`type`.toString,
      g.status.toString,
      g.code,
      Some(g.parents).find(_.nonEmpty).map(_.mkString(",")),
      g.country_code,
      g.title,
      g.start_date,
      g.timezone,
      g.end_date,
      g.input_type,
      g.input_point,
      Some(g.tags).find(_.nonEmpty).map(_.mkString(","))
    )
  }

}


private[repositories] trait GameLimitTable {

  protected val gameLimitTableQuery = TableQuery[GameLimitTable]

  class GameLimitTable(tag: Tag) extends Table[(UUID, GameLimit)](tag, "REF_GAME_LIMIT") {

    implicit val myTimestampColumnType: JdbcType[Instant] with BaseTypedType[Instant] = MappedColumnType.base[Instant, Timestamp](
      dt => new java.sql.Timestamp(dt.toEpochMilli),
      ts => Instant.ofEpochMilli(ts.getTime)
    )

    implicit val gameLimitTypeColumnType: JdbcType[GameLimitType.Value] with BaseTypedType[GameLimitType.Value] = MappedColumnType.base[GameLimitType.Value, String](
      e => e.toString,
      s => GameLimitType.withName(s)
    )

    implicit val gameLimitUnitColumnType: JdbcType[GameLimitUnit.Value] with BaseTypedType[GameLimitUnit.Value] = MappedColumnType.base[GameLimitUnit.Value, String](
      e => e.toString,
      s => GameLimitUnit.withName(s)
    )

    override def * = (game_id, `type`, unit, unit_value, value) <> (create, extract)

    def game_id = column[UUID]("game_id")

    def `type` = column[GameLimitType.Value]("type", O.Length(20, varying = true))

    def unit = column[GameLimitUnit.Value]("unit", O.Length(20, varying = true))

    def unit_value = column[Option[Int]]("unit_value")

    //def game = foreignKey("GAME_FK", game_id, TableQuery[GameTable])(_.id)

    def value = column[Int]("value")

    def create(t: (UUID, GameLimitType.Value, GameLimitUnit.Value, Option[Int], Int)): (UUID, GameLimit) = (t._1, GameLimit(`type` = t._2, unit = t._3, unit_value = t._4, value = t._5))

    def extract(t: (UUID, GameLimit)) = Some((t._1, t._2.`type`, t._2.unit, t._2.unit_value, t._2.value))
  }

}


private[repositories] trait GamePrizeTable {

  protected val gamePrizeTableQuery = TableQuery[GamePrizeTable]

  class GamePrizeTable(tag: Tag) extends Table[(UUID, GamePrize)](tag, "REF_GAME_PRIZE") {

    implicit val myTimestampColumnType: JdbcType[Instant] with BaseTypedType[Instant] = MappedColumnType.base[Instant, Timestamp](
      dt => new java.sql.Timestamp(dt.toEpochMilli),
      ts => Instant.ofEpochMilli(ts.getTime)
    )

    implicit val gameLimitTypeColumnType: JdbcType[GameLimitType.Value] with BaseTypedType[GameLimitType.Value] = MappedColumnType.base[GameLimitType.Value, String](
      e => e.toString,
      s => GameLimitType.withName(s)
    )

    implicit val gameLimitUnitColumnType: JdbcType[GameLimitUnit.Value] with BaseTypedType[GameLimitUnit.Value] = MappedColumnType.base[GameLimitUnit.Value, String](
      e => e.toString,
      s => GameLimitUnit.withName(s)
    )

    override def * = (game_id, id, prize_id, start_date, end_date, quantity) <> (create, extract)

    def id = column[UUID]("id", O.PrimaryKey)

    def game_id = column[UUID]("game_id")

    def prize_id = column[UUID]("prize_id")

    def start_date = column[Instant]("start_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    def end_date = column[Instant]("end_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    //def game = foreignKey("GAME_FK", game_id, TableQuery[GameTable])(_.id)

    def quantity = column[Int]("quantity")

    def create(t: (UUID, UUID, UUID, Instant, Instant, Int)): (UUID, GamePrize) = (t._1, GamePrize(id = t._2, prize_id = t._3, start_date = t._4, end_date = t._5, quantity = t._6))

    def extract(t: (UUID, GamePrize)) = Some((t._1, t._2.id, t._2.prize_id, t._2.start_date, t._2.end_date, t._2.quantity))
  }

}


private[repositories] trait GameEanTable {

  protected val gameEanTableQuery = TableQuery[GameEanTable]

  class GameEanTable(tag: Tag) extends Table[(UUID, String)](tag, "REF_GAME_EAN") {

    def pk = primaryKey("pk_a", (game_id, ean))

    def game_id = column[UUID]("game_id")

    def ean = column[String]("ean", O.Length(30, varying = true))

    override def * = (game_id, ean)
  }

}


private[repositories] trait GameFreeCodeTable {

  protected val gameFreecodeTableQuery = TableQuery[GameFreeCodeTable]

  class GameFreeCodeTable(tag: Tag) extends Table[(UUID, String)](tag, "REF_GAME_FREECODE") {

    def pk = primaryKey("pk_a", (game_id, freecode))

    def game_id = column[UUID]("game_id")

    def freecode = column[String]("freecode", O.Length(30, varying = true))

    override def * = (game_id, freecode)
  }

}
