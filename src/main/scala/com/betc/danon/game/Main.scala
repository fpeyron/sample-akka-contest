package com.betc.danon.game

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.betc.danon.game.actors.{BoGameActor, BoPrizeActor, ClusterListenerActor, GameManagerActor}
import com.betc.danon.game.repositories.{GameRepository, InstantwinRepository, PrizeRepository}
import com.betc.danon.game.routes._
import com.betc.danon.game.utils.CustomMySqlProfile.api.Database
import com.betc.danon.game.utils.HttpSupport

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
  implicit val repository: Repository = new Repository

  // initialization schemas
  repository.prize.schemaCreate()
  repository.game.schemaCreate()
  repository.instantwin.schemaCreate()

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
  val mainRoute = new MainRoute(gameActor, prizeActor, clusterSingletonProxy)
  val bindingFuture = Http().bindAndHandle(mainRoute.routes, Config.Api.hostname, Config.Api.port)

  // logger
  val logger = Logging(system, getClass)
  logger.info(s"Server online at      http://${Config.Api.hostname}:${Config.Api.port}")
  logger.info(s"Server online info    http://${Config.Api.hostname}:${Config.Api.port}/info")
  logger.info(s"Server online health  http://${Config.Api.hostname}:${Config.Api.port}/health")
  logger.info(s"Swagger description   http://${Config.Api.hostname}:${Config.Api.port}/api-docs/swagger.json")
  logger.info(s"Swagger ui            http://${Config.Api.hostname}:${Config.Api.port}/swagger")
}

class MainRoute(val gameActor: ActorRef, val prizeActor: ActorRef, val clusterSingletonProxy: ActorRef)(implicit val ec: ExecutionContext, implicit val materializer: ActorMaterializer)
  extends HttpSupport with BoGameRoute with SwaggerRoute with BoPrizeRoute with PartnerRoute with SwaggerUiRoute with HealthRoute {

  val routes: Route = healthCheckRoute ~ gameRoute ~ prizeRoute ~ partnerRoute ~ swaggerRoute ~ swaggerUiRoute
}

class Repository(implicit val ec: ExecutionContext, implicit val database: Database, val materializer: ActorMaterializer) extends PrizeRepository with GameRepository with InstantwinRepository