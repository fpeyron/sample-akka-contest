package com.betc.danon.game

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import com.betc.danon.game.actors._
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

  // Start Actor Proxy
  implicit val clusterSingletonProxy: ActorRef = system.actorOf(
    props = ClusterSingletonProxy.props(
      singletonManagerPath = clusterSingleton.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system).withRole(None)
    ),
    name = s"${GameManagerActor.name}Proxy"
  )
  val customerClusterProxy: ActorRef = ClusterSharding(system).startProxy(
    typeName = CustomerWorkerActor.typeName,
    extractEntityId = CustomerWorkerActor.extractEntityId,
    extractShardId = CustomerWorkerActor.extractShardId,
    role = None
  )
  val gameClusterProxy: ActorRef = ClusterSharding(system).startProxy(
    typeName = GameWorkerActor.typeName,
    extractEntityId = GameWorkerActor.extractEntityId,
    extractShardId = GameWorkerActor.extractShardId,
    role = None
  )

  // Start actors
  val customerCluster: ActorRef = ClusterSharding(system).start(
    typeName = CustomerWorkerActor.typeName,
    entityProps = CustomerWorkerActor.props(gameClusterProxy),
    settings = ClusterShardingSettings(system),
    extractEntityId = CustomerWorkerActor.extractEntityId,
    extractShardId = CustomerWorkerActor.extractShardId
  )
  val gameCluster: ActorRef = ClusterSharding(system).start(
    typeName = GameWorkerActor.typeName,
    entityProps = GameWorkerActor.props(customerClusterProxy),
    settings = ClusterShardingSettings(system),
    extractEntityId = GameWorkerActor.extractEntityId,
    extractShardId = GameWorkerActor.extractShardId
  )
  val clusterSingleton: ActorRef = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = GameManagerActor.props(gameClusterProxy),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    name = GameManagerActor.name
  )
  val boGameActor: ActorRef = system.actorOf(BoGameActor.props, BoGameActor.Name)
  val boPrizeActor: ActorRef = system.actorOf(BoPrizeActor.props, BoPrizeActor.name)
  val clusterListenerActor = system.actorOf(ClusterListenerActor.props, ClusterListenerActor.name)

  // start http services
  implicit val routeContext: RouteContext = RouteContext(boGameActor = boGameActor, boPrizeActor = boPrizeActor, clusterSingletonProxy = clusterSingletonProxy, customerCluster = customerCluster)

  val bindingFuture = Http().bindAndHandle(DebuggingDirectives.logRequestResult("API route", Logging.DebugLevel)(MainRoute().routes), Config.Api.hostname, Config.Api.port)

  // logger
  val logger = Logging(system, getClass)
  logger.info(s"Server online at      http://${Config.Api.hostname}:${Config.Api.port}")
  logger.info(s"Server online info    http://${Config.Api.hostname}:${Config.Api.port}/info")
  logger.info(s"Server online health  http://${Config.Api.hostname}:${Config.Api.port}/health")
  logger.info(s"Swagger description   http://${Config.Api.hostname}:${Config.Api.port}/api-docs/swagger.json")
  logger.info(s"Swagger ui            http://${Config.Api.hostname}:${Config.Api.port}/swagger")
}

case class RouteContext(boGameActor: ActorRef, boPrizeActor: ActorRef, clusterSingletonProxy: ActorRef, customerCluster: ActorRef)

case class MainRoute()
                    (implicit
                     val ec: ExecutionContext,
                     val materializer: ActorMaterializer,
                     val query: Query,
                     val clusterSingletonProxy: ActorRef,
                     val context: RouteContext
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