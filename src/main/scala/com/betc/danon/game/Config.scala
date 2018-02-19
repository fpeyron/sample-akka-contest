package com.betc.danon.game


import akka.util.Timeout
import com.betc.danon.game.utils.AuthenticateSupport.UserEntry
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object Config {

  object Api {
    lazy val port: Int = ConfigFactory.load().getInt("api.http.port")
    lazy val hostname: String = ConfigFactory.load().getString("api.http.hostname")
    lazy val timeout: Timeout = Timeout(ConfigFactory.load().getLong("api.http.timeout").milliseconds)
    lazy val credentials: Seq[UserEntry] = ConfigFactory.load().getObjectList("api.credentials").asScala.map { e =>
      UserEntry(
        username = e.toConfig.getString("username"),
        password = e.toConfig.getString("password"),
        countryCode = e.toConfig.getString("countryCode"),
        scopes = e.toConfig.getStringList("scopes").asScala
      )
    }
  }

  object Cluster {
    lazy val port: Int = ConfigFactory.load().getInt("akka.remote.netty.tcp.port")
    lazy val hostname: String = ConfigFactory.load().getString("akka.remote.netty.tcp.hostname")
    lazy val shardCount: Int = ConfigFactory.load().getInt("akka.cluster.shardCount")
  }

}
