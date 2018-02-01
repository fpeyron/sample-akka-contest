package fr.sysf.sample

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import fr.sysf.sample.CustomMySqlProfile.api.Database
import fr.sysf.sample.actors.{ClusterListenerActor, ClusterSingletonActor, BoGameActor, BoPrizeActor}
import fr.sysf.sample.repositories.{GameRepository, InstantwinRepository, PrizeRepository}
import fr.sysf.sample.routes._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends App with RouteConcatenation with HttpSupport {

  // needed to run the route
  implicit val system: ActorSystem = ActorSystem("fusion-game")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatMap in the end
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // needed for shutdown properly
  sys.addShutdownHook(system.terminate())

  // Start Actor Singleton
  val clusterSingleton = system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = ClusterSingletonActor.props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ),
    name = ClusterSingletonActor.name
  )

  // Start Actor Singleton Proxy
  val clusterSingletonProxy = system.actorOf(
    props = ClusterSingletonProxy.props(
      singletonManagerPath = clusterSingleton.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system).withRole(None)
    ),
    name = s"${ClusterSingletonActor.name}Proxy"
  )

  // Start repository
  implicit val database: Database = Database.forConfig("slick.db")
  implicit val repository: Repository = new Repository

  // initialization schemas
  repository.prize.schemaCreate()
  repository.game.schemaCreate()
  repository.instantwin.schemaCreate()

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
  logger.info(s"Server online at http://${Config.Api.hostname}:${Config.Api.port}/")
  logger.info(s"Swagger description http://${Config.Api.hostname}:${Config.Api.port}/api-docs/swagger.json")
}

class MainRoute(val gameActor: ActorRef, val prizeActor: ActorRef, val clusterSingletonProxy: ActorRef)(implicit val ec:ExecutionContext, implicit val materializer: ActorMaterializer)
  extends HttpSupport with BoGameRoute with SwaggerRoute with BoPrizeRoute {

  val routes: Route = corsHandler(gameRoute ~ prizeRoute ~ HttpSupport.healthCheckRoute ~ swaggerRoute)
}
 class Repository(implicit val ec : ExecutionContext, implicit val database: Database, val materializer: ActorMaterializer) extends PrizeRepository with GameRepository with InstantwinRepository