package com.betc.danon.game.utils

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import com.betc.danon.game.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions


object AuthenticateSupport {

  private val users = Config.Api.credentials

  def asAuthenticated(route: UserContext => Route)(implicit ec: ExecutionContext): Route =
    authenticateBasicAsync(realm = "secure site", userPassAuthenticator)(route)

  import com.roundeights.hasher.Implicits._

  private def hasher: String => String = _.sha256

  private def userPassAuthenticator(credentials: Credentials)(implicit ec: ExecutionContext): Future[Option[UserContext]] =
    credentials match {
      case p@Credentials.Provided(username) =>
        Future {
          // potentially
          users.find(u => u.username == username && p.verify(u.password, hasher)).map(u => UserContext(username = u.username, countryCode = u.countryCode))
        }
      case _ => Future.successful(None)
    }

  /**
    * Case class containing basic information about user
    *
    * @param username The username as used when authenticating
    * @param password The hashed password to verify against
    */
  sealed case class UserEntry(username: String, password: String, countryCode: String, scopes: Seq[String] = Seq.empty)

  implicit def userEntryToUserContext(u: UserEntry): UserContext = UserContext(username = u.username, countryCode = u.countryCode)

  sealed case class UserContext(username: String, countryCode: String)

}


