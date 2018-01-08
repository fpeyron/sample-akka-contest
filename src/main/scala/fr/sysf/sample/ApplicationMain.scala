package fr.sysf.sample

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import fr.sysf.sample.game.{GameActor, GameService}
import fr.sysf.sample.prize.{PrizeActor, PrizeService}
import fr.sysf.sample.swagger.SwaggerDocService

import scala.concurrent.ExecutionContextExecutor

object ApplicationMain extends App with RouteConcatenation {

  // configurations
  val config = ConfigFactory.parseString(
    s"""
       |akka {
       |  loglevel = INFO
       |  stdout-loglevel = INFO
       |}
       |app {
       |  http-service {
       |    address = "0.0.0.0"
       |    port = 8080
       |  }
       |}
       """.stripMargin).withFallback(ConfigFactory.load())
  val address = config.getString("app.http-service.address")
  val port = config.getInt("app.http-service.port")

  // needed to run the route
  implicit val system: ActorSystem = ActorSystem("akka-http-sample", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatMap in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // needed for shutdown properly
  sys.addShutdownHook(system.terminate())

  // Start actors
  val contestActor = system.actorOf(Props[GameActor])
  val prizeActor = system.actorOf(Props[PrizeActor])

  // start http services
  //  val routes = new HelloService(hello).route ~ new ContestService(contact).route ~ SwaggerDocService.routes
  val routes = SwaggerDocService.routes ~ new GameService(contestActor).route ~ new PrizeService(prizeActor).route
  //val bindingFuture = Http().bindAndHandle(routes, address, port)

  // logger
  val logger = Logging(system, getClass)

  logger.info(s"Server online at http://$address:$port/")
  logger.info(s"Swagger description http://$address:$port/api-docs/swagger.json")
  Http().bindAndHandle(routes, address, port)
}
