package fr.sysf.sample

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import fr.sysf.sample.actors.{ClusterListenerActor, GameActor, PrizeActor}
import fr.sysf.sample.routes._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object ApplicationMain extends App with RouteConcatenation with HttpSupport {

  // configurations
  implicit val config: Config = ConfigFactory.load()
  val address = config.getString("app.http.hostname")
  val port = config.getInt("app.http.port")

  // needed to run the route
  implicit val system: ActorSystem = ActorSystem("fusion-game", config)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatMap in the end
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // needed for shutdown properly
  sys.addShutdownHook(system.terminate())

  // Start actors
  val gameActor: ActorRef = system.actorOf(GameActor.props, GameActor.name)
  val prizeActor: ActorRef = system.actorOf(PrizeActor.props, PrizeActor.name)

  // Start Actor clusterListener
  val clusterListenerActor = system.actorOf(ClusterListenerActor.props, ClusterListenerActor.name)

  // start http services
  val mainRoute = new MainRoute(gameActor, prizeActor)
  val bindingFuture = Http().bindAndHandle(mainRoute.routes, address, port)

  // logger
  val logger = Logging(system, getClass)
  logger.info(s"Server online at http://$address:$port/")
  logger.info(s"Swagger description http://$address:$port/api-docs/swagger.json")
}

class MainRoute(val gameActor: ActorRef, val prizeActor: ActorRef)(implicit val ec:ExecutionContext)
  extends HttpSupport with GameRoute with SwaggerRoute with PrizeRoute {

  val routes: Route = gameRoute ~ swaggerRoute ~ prizeRoute  ~ HttpSupport.healthCheckRoute
}