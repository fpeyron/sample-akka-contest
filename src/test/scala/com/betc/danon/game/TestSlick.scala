package com.betc.danon.game

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GamePrize, GameStatus, GameType}
import com.betc.danon.game.models.PrizeDomain.{Prize, PrizeType}
import com.betc.danon.game.utils.CustomMySqlProfile.api.Database
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor

object TestSlick extends App {

  // configurations
  val config = ConfigFactory.parseString(
    s"""
       |h2mem1 = {
       |  url = "jdbc:h2:mem:test1"
       |  driver = org.h2.Driver
       |  connectionPool = disabled
       |  keepAliveConnection = true
       |}
       """.stripMargin).withFallback(ConfigFactory.load())

  // needed to run the routeO
  implicit val system: ActorSystem = ActorSystem("TestSlick", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatMap in the end
  implicit val ec: ExecutionContextExecutor = system.dispatcher


  implicit val database: Database = Database.forConfig("slick.db")
  implicit val repository: Repository = new Repository


  repository.game.schemaDropCreate()
  repository.prize.schemaDropCreate()
  repository.instantwin.schemaDropCreate()

  /*
  for {
    _ <- prizeRepository.prize.schemaCreateFuture
    _ <- gameRepository.game.schemaCreateFuture
  } ()
  */

  val ids = (1 to 2).map((_, UUID.randomUUID())).toList

  for {
    (i, uuid) <- ids
    _ <- repository.prize.create(Prize(code = "sdfdsf", id = UUID.randomUUID(), countryCode = "FR", `type` = PrizeType.Point, label = s"$i-myLabel"))
    _ <- repository.game.create(Game(
      id = uuid,
      `type` = GameType.Instant,
      status = GameStatus.Activated,
      code = s"instant-$i",
      countryCode = "CA",
      startDate = Instant.now, timezone = "+02:00", endDate = Instant.now.plusSeconds(1000000l), inputType = GameInputType.Other,
      limits = Seq(GameLimit(`type` = GameLimitType.Participation, unit = GameLimitUnit.Day, unit_value = Some(1), value = 10),
        GameLimit(`type` = GameLimitType.Win, unit = GameLimitUnit.Game, unit_value = None, value = 10))
    ))
    _ <- repository.game.addPrize(uuid, GamePrize(id = UUID.randomUUID(), prize_id = uuid, start_date = Instant.now(), end_date = Instant.now().plusSeconds(10000l), quantity = 10))
    _ <- repository.game.addPrize(uuid, GamePrize(id = UUID.randomUUID(), prize_id = uuid, start_date = Instant.now(), end_date = Instant.now().plusSeconds(10000l), quantity = 5))
    _ <- repository.game.addPrize(uuid, GamePrize(id = UUID.randomUUID(), prize_id = uuid, start_date = Instant.now(), end_date = Instant.now().plusSeconds(10000l), quantity = 13))
  } ()

  println("repository.prize.getAll: ")
  repository.prize.fetchBy().runForeach(println(_))

  println("repository.game.findBy: ")
  repository.game.fetchBy(country_code = Some("CA")).runForeach { r => println(r) }

  println("repository.game.getById: ")
  repository.game.getById(ids.head._2).foreach(r => println(r))

}