package fr.sysf.sample

import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object Config {

  object Api {
    lazy val port: Int = ConfigFactory.load().getInt("api.http.port")
    lazy val hostname: String = ConfigFactory.load().getString("api.http.hostname")
    lazy val timeout: Timeout = Timeout(ConfigFactory.load().getLong("api.http.timeout").milliseconds)
  }

  object Cluster {
//    lazy val shardCount: Int = ConfigFactory.load().getInt("homeworkzen.cluster.shardCount")
    lazy val port: Int = ConfigFactory.load().getInt("akka.remote.netty.tcp.port")
    lazy val hostname: String = ConfigFactory.load().getString("akka.remote.netty.tcp.hostname")
  }


}
