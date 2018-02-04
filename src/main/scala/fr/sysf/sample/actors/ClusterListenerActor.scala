package fr.sysf.sample.actors

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

object ClusterListenerActor {

  val name = "clusterListener"

  def props: Props = Props(new ClusterListenerActor)

  case object GetMemberNodes

}

class ClusterListenerActor extends Actor with ActorLogging {

  import ClusterListenerActor._

  val cluster = Cluster(context.system)

  //noinspection ActorMutableStateInspection
  private var members = Set.empty[Address]

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {

    case GetMemberNodes =>
      sender() ! members
    case MemberJoined(member) =>
      log.info("Member joined: {}", member.address)
      members += member.address
    case MemberUp(member) =>
      log.info("Member up: {}", member.address)
      members += member.address
    case MemberRemoved(member, _) =>
      log.info("Member removed: {}", member.address)
      members -= member.address
  }
}
