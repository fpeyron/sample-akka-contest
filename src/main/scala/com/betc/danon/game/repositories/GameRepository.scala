package com.betc.danon.game.repositories

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GamePrize, GameStatus, GameType}
import com.betc.danon.game.utils.CustomMySqlProfile.api._
import com.betc.danon.game.utils.StreamUtil.AccumulateWhileUnchanged
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait GameRepository extends GameTable with GameLimitTable with GamePrizeTable with GameEanTable with GameFreeCodeTable {

  private[repositories] implicit val database: Database
  private[repositories] implicit val ec: ExecutionContext

  object game {

    /**
      * Game
      */
    def create(game: Game): Future[Unit] = database.run {
      DBIO.seq(
        gameTableQuery += game,
        gameLimitTableQuery ++= game.limits.map(l => (game.id, l)),
        gameEanTableQuery ++= game.inputEans.map(l => (game.id, l)),
        gameFreecodeTableQuery ++= game.inputFreecodes.map(l => (game.id, l))
      ).transactionally
    }


    def update(game: Game): Future[Unit] = database.run {
      DBIO.seq(
        gameTableQuery.filter(_.id === game.id).update(game),
        gameLimitTableQuery.filter(_.game_id === game.id).delete,
        gameLimitTableQuery ++= game.limits.map(l => (game.id, l)),
        gameEanTableQuery.filter(_.game_id === game.id).delete,
        gameEanTableQuery ++= game.inputEans.map(l => (game.id, l)),
        gameFreecodeTableQuery.filter(_.game_id === game.id).delete,
        gameFreecodeTableQuery ++= game.inputFreecodes.map(l => (game.id, l))
      ).transactionally
    }


    def delete(game_id: UUID): Future[Unit] = database.run {
      DBIO.seq(
        gamePrizeTableQuery.filter(_.game_id === game_id).delete,
        gameLimitTableQuery.filter(_.game_id === game_id).delete,
        gameEanTableQuery.filter(_.game_id === game_id).delete,
        gameFreecodeTableQuery.filter(_.game_id === game_id).delete,
        gameTableQuery.filter(_.id === game_id).delete
      )
    }


    def updateStatus(game_id: UUID, status: GameStatus.Value): Future[Int] = database.run {
      (for {c <- gameTableQuery if c.id === game_id} yield c.status).update(status.toString)
    }


    def findByIds(ids: Seq[UUID]): Future[Seq[Game]] = {
      database.run(gameTableQuery.filter(_.id inSet ids).to[Seq].result)
    }


    def findByParentId(parentId: UUID): Future[Seq[Game]] = database.run {
      val ids = gameLimitTableQuery.filter(_.parent_id === parentId).map(_.game_id)
      gameTableQuery.filter(_.id in ids).to[Seq].result
    }

    def getById(game_id: UUID, extensions: Seq[GameExtension.Value] = Seq.empty): Future[Option[Game]] = {

      val query: DBIO[(Option[Game], Seq[GameLimit], Seq[String], Seq[String], Seq[GamePrize])] = for {
        game <- gameTableQuery.filter(_.id === game_id).result.headOption
        gameLimit <- if (game.isDefined && extensions.contains(GameExtension.limits)) gameLimitTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gameEans <- if (game.isDefined && extensions.contains(GameExtension.eans)) gameEanTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gameFreeCodes <- if (game.isDefined && extensions.contains(GameExtension.freecodes)) gameFreecodeTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
        gamePrizes <- if (game.isDefined && extensions.contains(GameExtension.prizes)) gamePrizeTableQuery.filter(_.game_id === game_id).result.map(_.map(_._2)) else DBIO.successful(Seq.empty)
      } yield (game, gameLimit, gameEans, gameFreeCodes, gamePrizes)

      database.run(query).map(r => r._1.map(_.copy(limits = r._2, inputEans = r._3, inputFreecodes = r._4, prizes = r._5)))
    }


    def findByCode(code: String): Source[Game, NotUsed] = Source.fromPublisher {
      database.stream {
        gameTableQuery.filter(_.code === code).to[Seq].result
      }
    }

    def findByTagsAndCodes(tags: Seq[String], codes: Seq[String]): Source[Game, NotUsed] = Source.fromPublisher {
      database.stream {
        gameTableQuery
          .filter(game => tags.map(tag => game.tags.getOrElse("") like s"%$tag%").reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean]))
          .filter(game => if (codes.nonEmpty) game.code.inSetBind(codes) else true: Rep[Boolean])
          .joinLeft(gameLimitTableQuery).on(_.id === _.game_id)
          .to[List]
          .result
      }
        .mapResult(r => r._1.copy(limits = r._2.map(_._2).toSeq))
    }
      .via(new AccumulateWhileUnchanged(_.id))
      .map(l => l.head.copy(limits = l.map(_.limits).reduceLeft(_ ++ _)))


    def fetchExtendedBy(
                         country_code: Option[String] = None,
                         status: Iterable[GameStatus.Value] = Iterable.empty,
                         types: Iterable[GameType.Value] = Iterable.empty,
                         code: Option[String] = None
                       ): Source[Game, NotUsed] = Source.fromPublisher {
      database.stream {
        val query = gameTableQuery
          .filter(row => (if (types.isEmpty) None else Some(types)).map(s => row.`type` inSet s.map(_.toString)).getOrElse(true: Rep[Boolean]))
          .filter(row => (if (status.isEmpty) None else Some(status)).map(s => row.status inSet s.map(_.toString)).getOrElse(true: Rep[Boolean]))
          .filter(row => if (country_code.isDefined) row.country_code === country_code.get else true: Rep[Boolean])
          .filter(row => if (code.isDefined) row.code === code.get else true: Rep[Boolean])
          .joinLeft(gameLimitTableQuery).on(_.id === _.game_id)
          .to[List]
        query.result
      }.mapResult(r => r._1.copy(limits = r._2.map(_._2).toSeq))
    }
      .via(new AccumulateWhileUnchanged(_.id))
      .map(l => l.head.copy(limits = l.map(_.limits).reduceLeft(_ ++ _)))


    def fetchByCode(
                         code: String
                       ): Source[Game, NotUsed] = Source.fromPublisher {
      database.stream {
        val query = gameTableQuery
          .filter(_.code === code)
          .joinLeft(gameLimitTableQuery).on(_.id === _.game_id)
          .joinLeft(gameEanTableQuery).on(_._1.id === _.game_id)
          .to[List]
        query.result
      }.mapResult(r => r._1._1.copy(limits = r._1._2.map(_._2).toSeq, inputEans = r._2.map(_._2).toSeq))
    }
      .via(new AccumulateWhileUnchanged(_.id))
      .map(l => l.head.copy(limits = l.map(_.limits).reduceLeft(_ ++ _), inputEans = l.map(_.inputEans).reduceLeft(_ ++ _)))



    def fetchBy(
                 country_code: Option[String] = None,
                 status: Iterable[GameStatus.Value] = Iterable.empty,
                 types: Iterable[GameType.Value] = Iterable.empty,
                 code: Option[String] = None
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

    implicit val gameStatusTypeColumnType: JdbcType[GameStatus.Value] with BaseTypedType[GameStatus.Value] = MappedColumnType.base[GameStatus.Value, String](
      e => e.toString,
      s => GameStatus.withName(s)
    )

    implicit val gameInputTypeColumnType: JdbcType[GameInputType.Value] with BaseTypedType[GameInputType.Value] = MappedColumnType.base[GameInputType.Value, String](
      e => e.toString,
      s => GameInputType.withName(s)
    )

    override def * = (id, `type`, status, code, country_code, title, picture, description , start_date, timezone, end_date, input_type, input_point, tags) <> (create, extract)

    def id = column[UUID]("id", O.PrimaryKey)

    def `type` = column[String]("type", O.Length(10, varying = true))

    def status = column[String]("status", O.Length(10, varying = true))

    def code = column[String]("code", O.Length(36, varying = true))

    def country_code = column[String]("country_code", O.Length(2, varying = true))

    def title = column[Option[String]]("title", O.Length(50, varying = true))

    def picture = column[Option[String]]("picture", O.Length(50, varying = true))

    def description = column[Option[String]]("description", O.Length(255, varying = true))

    def start_date = column[Instant]("start_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    def timezone = column[String]("timezone", O.Length(10, varying = true))

    def end_date = column[Instant]("end_date", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    def input_type = column[GameInputType.Value]("input_type", O.Length(20, varying = true))

    def input_point = column[Option[Int]]("input_point")

    def tags = column[Option[String]]("tags", O.Length(255, varying = true))

    def create(t: (UUID, String, String, String, String, Option[String], Option[String], Option[String], Instant, String, Instant, GameInputType.Value, Option[Int], Option[String])) =
      Game(
        id = t._1,
        `type` = GameType.withName(t._2),
        status = GameStatus.withName(t._3),
        code = t._4,
        countryCode = t._5,
        title = t._6,
        picture = t._7,
        description = t._8,
        startDate = t._9,
        timezone = t._10,
        endDate = t._11,
        inputType = t._12,
        inputPoint = t._13,
        tags = t._14.map(_.split(",").toSeq).getOrElse(Seq.empty)
      )

    def extract(g: Game) = Option(
      g.id,
      g.`type`.toString,
      g.status.toString,
      g.code,
      g.countryCode,
      g.title,
      g.picture,
      g.description,
      g.startDate,
      g.timezone,
      g.endDate,
      g.inputType,
      g.inputPoint,
      Some(g.tags).find(_.nonEmpty).map(_.mkString(","))
    )

    def idx_code = index("idx_code", code, unique = false)
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

    override def * = (game_id, `type`, unit, unit_value, value, parent_id) <> (create, extract)

    def game_id = column[UUID]("game_id")

    def `type` = column[GameLimitType.Value]("type", O.Length(20, varying = true))

    def unit = column[GameLimitUnit.Value]("unit", O.Length(20, varying = true))

    def unit_value = column[Option[Int]]("unit_value")

    //def game = foreignKey("GAME_FK", game_id, TableQuery[GameTable])(_.id)

    def value = column[Int]("value")

    def parent_id = column[Option[UUID]]("parent_id")

    def create(t: (UUID, GameLimitType.Value, GameLimitUnit.Value, Option[Int], Int, Option[UUID])): (UUID, GameLimit) = (t._1, GameLimit(`type` = t._2, unit = t._3, unit_value = t._4, value = t._5, parent_id = t._6))

    def extract(t: (UUID, GameLimit)) = Some((t._1, t._2.`type`, t._2.unit, t._2.unit_value, t._2.value, t._2.parent_id))

    def idx_game_id = index("idx_game_id", game_id, unique = false)

    def idx_parent_id = index("idx_parent_id", game_id, unique = false)
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

    def idx_game_id = index("idx_game_id", game_id, unique = false)
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


object GameExtension extends Enumeration {
  val limits: GameExtension.Value = Value("limits")
  val eans: GameExtension.Value = Value("eans")
  val freecodes: GameExtension.Value = Value("freecodes")
  val prizes: GameExtension.Value = Value("prizes")
  val all = Seq(limits, eans, freecodes, prizes)
}
