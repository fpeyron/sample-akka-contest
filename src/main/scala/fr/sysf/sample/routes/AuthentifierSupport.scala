package fr.sysf.sample.routes

import akka.http.scaladsl.server
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials

import scala.concurrent.{ExecutionContext, Future}


object AuthentifierSupport {

  def asAuthentified(route: UserContext => server.Route)(implicit ec: ExecutionContext): Route =
      authenticateBasicAsync(realm = "secure site", userPassAuthenticator)(route)

  private def userPassAuthenticator(credentials: Credentials)(implicit ec: ExecutionContext): Future[Option[UserContext]] =
    credentials match {
      case p@Credentials.Provided(username) =>
        Future {
          // potentially
          users.filter(u => u.username == username && p.verify(u.password)).headOption.map(u => UserContext(username = u.username, country_code = u.country_code))
        }
      case _ => Future.successful(None)
    }


  /**
    * Case class containing basic information about user
    *
    * @param username The username as used when authenticating
    * @param password   The hashed password to verify against
    */
  sealed case class UserEntry(username: String, password: String, country_code: String)
  sealed case class UserContext(username: String, country_code: String)

  private val users = List(
    UserEntry("admin_fr", "p4ssw0rd", "FR"),
    UserEntry("admin_jp", "p4ssw0rd", "JP"),
    UserEntry("admin_ca", "p4ssw0rd", "CA")
  )

}


