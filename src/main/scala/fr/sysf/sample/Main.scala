package fr.sysf.sample

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import fr.sysf.sample.actors.{ClusterListenerActor, ClusterSingletonActor, GameActor, PrizeActor}
import fr.sysf.sample.routes._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object ApplicationMain extends App with RouteConcatenation with HttpSupport {

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

  // Start actors
  val gameActor: ActorRef = system.actorOf(GameActor.props, GameActor.Name)
  val prizeActor: ActorRef = system.actorOf(PrizeActor.props, PrizeActor.name)

  // Start Actor clusterListener
  val clusterListenerActor = system.actorOf(ClusterListenerActor.props, ClusterListenerActor.name)

  // start http services
  val mainRoute = new MainRoute(gameActor, prizeActor)
  val bindingFuture = Http().bindAndHandle(mainRoute.routes, Config.Api.hostname, Config.Api.port)

  // logger
  val logger = Logging(system, getClass)
  logger.info(s"Server online at http://${Config.Api.hostname}:${Config.Api.port}/")
  logger.info(s"Swagger description http://${Config.Api.hostname}:${Config.Api.port}/api-docs/swagger.json")
}

class MainRoute(val gameActor: ActorRef, val prizeActor: ActorRef)(implicit val ec:ExecutionContext)
  extends HttpSupport with GameRoute with SwaggerRoute with PrizeRoute {

  val routes: Route = gameRoute ~ prizeRoute ~ HttpSupport.healthCheckRoute ~ swaggerRoute
}