package fr.sysf.sample.actors

import akka.actor.{Actor, ActorLogging, Props}
import fr.sysf.sample.Config

object ClusterSingletonActor {

  def props: Props = Props[ClusterSingletonActor]

  final val name = "clusterSingleton"

  /*
    def doItAsynchronously(implicit ec: ExecutionContext): Future[String] = {
      Future.successful("sdf")
    }
    */
}

class ClusterSingletonActor extends Actor with ActorLogging {

  //import context.dispatcher
  //import akka.pattern.pipe

  override def receive = {
    case _ =>
      sender() ! s"${Config.Cluster.hostname}:${Config.Cluster.port}"

    //  ClusterSingletonActor.doItAsynchronously.pipeTo(self)
  }
}

