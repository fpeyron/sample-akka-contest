package fr.sysf.sample.utils

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions


object AuthenticateSupport {

  private val users = List(
    UserEntry("admin_fr", "p4ssw0rd", "FR"),
    UserEntry("admin_jp", "p4ssw0rd", "JP"),
    UserEntry("admin_ca", "p4ssw0rd", "CA")
  )

  def asAuthenticated(route: UserContext => Route)(implicit ec: ExecutionContext): Route =
    authenticateBasicAsync(realm = "secure site", userPassAuthenticator)(route)

  private def userPassAuthenticator(credentials: Credentials)(implicit ec: ExecutionContext): Future[Option[UserContext]] =
    credentials match {
      case p@Credentials.Provided(username) =>
        Future {
          // potentially
          users.find(u => u.username == username && p.verify(u.password)).map(u => UserContext(username = u.username, country_code = u.country_code))
        }
      case _ => Future.successful(None)
    }

  /**
    * Case class containing basic information about user
    *
    * @param username The username as used when authenticating
    * @param password The hashed password to verify against
    */
  sealed case class UserEntry(username: String, password: String, country_code: String)

  implicit def userEntryToUserContext(u: UserEntry): UserContext = UserContext(username = u.username, country_code = u.country_code)

  sealed case class UserContext(username: String, country_code: String)

}


