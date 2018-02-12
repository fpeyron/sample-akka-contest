package com.betc.danon.game

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import com.betc.danon.game.actors.{BoGameActor, BoPrizeActor, ClusterListenerActor, GameManagerActor}
import com.betc.danon.game.queries.CustomerQuery
import com.betc.danon.game.repositories.{GameRepository, InstantwinRepository, PrizeRepository}
import com.betc.danon.game.routes._
import com.betc.danon.game.utils.CustomMySqlProfile.api.Database
import com.betc.danon.game.utils.{HttpSupport, JdbcJournalReader, JournalReader}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends App with RouteConcatenation with HttpSupport {

  // needed to run the route
  implicit val system: ActorSystem = ActorSystem("fusion-game")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatMap in the end
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // needed for shutdown properly
  sys.addShutdownHook(system.terminate())

  // Start repository
  implicit val database: Database = Database.forConfig("slick.db")
  implicit val repository: Repository = Repository()

  // initialization schemas
  repository.prize.schemaCreate()
  repository.game.schemaCreate()
  repository.instantwin.schemaCreate()

  // initial journal reader
  implicit val journalReader: JournalReader = JdbcJournalReader()

  // initial query
  implicit val query: Query = Query()

  // Start Actor Singleton
  val clusterSingleton: ActorRef = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = GameManagerActor.props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    name = GameManagerActor.name
  )

  // Start Actor Singleton Proxy
  implicit val clusterSingletonProxy: ActorRef = system.actorOf(
    props = ClusterSingletonProxy.props(
      singletonManagerPath = clusterSingleton.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system).withRole(None)
    ),
    name = s"${GameManagerActor.name}Proxy"
  )

  // Start actors
  val gameActor: ActorRef = system.actorOf(BoGameActor.props, BoGameActor.Name)
  val prizeActor: ActorRef = system.actorOf(BoPrizeActor.props, BoPrizeActor.name)

  // Start Actor clusterListener
  val clusterListenerActor = system.actorOf(ClusterListenerActor.props, ClusterListenerActor.name)

  // start http services
  val mainRoute = MainRoute(gameActor, prizeActor, clusterSingletonProxy)
  val bindingFuture = Http().bindAndHandle(DebuggingDirectives.logRequestResult("API route", Logging.DebugLevel)(mainRoute.routes), Config.Api.hostname, Config.Api.port)

  // logger
  val logger = Logging(system, getClass)
  logger.info(s"Server online at      http://${Config.Api.hostname}:${Config.Api.port}")
  logger.info(s"Server online info    http://${Config.Api.hostname}:${Config.Api.port}/info")
  logger.info(s"Server online health  http://${Config.Api.hostname}:${Config.Api.port}/health")
  logger.info(s"Swagger description   http://${Config.Api.hostname}:${Config.Api.port}/api-docs/swagger.json")
  logger.info(s"Swagger ui            http://${Config.Api.hostname}:${Config.Api.port}/swagger")
}

case class MainRoute(gameActor: ActorRef, prizeActor: ActorRef, clusterSingletonProxy: ActorRef)
                    (implicit
                     val ec: ExecutionContext,
                     val materializer: ActorMaterializer,
                     val query: Query
                    ) extends HttpSupport with BoGameRoute with SwaggerRoute with BoPrizeRoute with PartnerRoute with SwaggerUiRoute with HealthRoute {

  val routes: Route = healthCheckRoute ~ gameRoute ~ prizeRoute ~ partnerRoute ~ swaggerRoute ~ swaggerUiRoute
}

case class Repository()(
  implicit val ec: ExecutionContext,
  val database: Database,
  val materializer: ActorMaterializer
) extends PrizeRepository with GameRepository with InstantwinRepository

case class Query()(
  implicit val ec: ExecutionContext,
  val materializer: ActorMaterializer,
  val repository: Repository,
  val journalReader: JournalReader
) extends CustomerQuery