package fr.sysf.sample.models

import java.time.Instant
import java.util.UUID

import fr.sysf.sample.DefaultJsonFormats
import fr.sysf.sample.models.GameDto.{GameInputType, GameLimitResponse, GameLineResponse, GameStatusType, GameType}
import fr.sysf.sample.routes.AuthentifierSupport.UserContext
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object GameEntity {

  case class Game(
                   id: UUID,
                   `type`: GameType.Value,
                   status: GameStatusType.Value,
                   parent_id: Option[UUID] = None,
                   reference: String,
                   country_code: String,
                   portal_code: Option[String] = None,
                   title: Option[String] = None,
                   start_date: Instant,
                   timezone: String,
                   end_date: Instant,
                   input_type: GameInputType.Value,
                   input_point: Option[Int] = None,
                   input_eans: Option[Seq[String]] = None,
                   input_freecodes: Option[Seq[String]] = None,
                   limits: Seq[GameLimitResponse] = Seq.empty,
                   lines: Seq[GameLineResponse] = Seq.empty
                 )

}

object GameDto {

  // Service
  case class GameListRequest(uc: UserContext, types: Option[String], status: Option[String])

  case class GameGetRequest(uc: UserContext, id: UUID)

  case class GameGetInstantwinRequest(uc: UserContext, game_id: UUID)

  case class GameCreateRequest(
                                @ApiModelProperty(position = 1, value = "type", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                `type`: Option[String],
                                @ApiModelProperty(position = 2, value = "reference", required = true, example = "MY_CONTEST")
                                reference: Option[String],
                                @ApiModelProperty(position = 3, value = "parent game", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                parent_id: Option[UUID],
                                @ApiModelProperty(position = 5, value = "portal code", required = false, example = "WW_DANON")
                                portal_code: Option[String],
                                @ApiModelProperty(position = 6, value = "title", example = "My new game")
                                title: Option[String],
                                @ApiModelProperty(position = 7, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                start_date: Option[Instant],
                                @ApiModelProperty(position = 8, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                end_date: Option[Instant],
                                @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                                timezone: Option[String],
                                @ApiModelProperty(position = 10, value = "input type", required = false, example = "FREE", allowableValues = "FREE,POINT,SKU")
                                input_type: Option[String],
                                @ApiModelProperty(position = 12, value = "input point", required = false, example = "10")
                                input_point: Option[Int],
                                @ApiModelProperty(position = 13, value = "input eans", required = false)
                                input_eans: Option[Seq[String]],
                                @ApiModelProperty(position = 14, value = "input freecodes", required = false)
                                input_freecodes: Option[Seq[String]],
                                @ApiModelProperty(position = 15, value = "participation limit")
                                limits: Option[Seq[GameLimitRequest]]
                              )

  case class GameUpdateRequest(
                                @ApiModelProperty(position = 2, value = "reference", required = true, example = "MY_CONTEST")
                                reference: Option[String],
                                @ApiModelProperty(position = 3, value = "parent game", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                parent_id: Option[UUID],
                                @ApiModelProperty(position = 5, value = "portal code", required = false, example = "WW_DANON")
                                portal_code: Option[String],
                                @ApiModelProperty(position = 6, value = "title", example = "My new game")
                                title: Option[String],
                                @ApiModelProperty(position = 7, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                start_date: Option[Instant],
                                @ApiModelProperty(position = 8, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                end_date: Option[Instant],
                                @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                                timezone: Option[String],
                                @ApiModelProperty(position = 10, value = "input type", required = false, example = "FREE", allowableValues = "FREE,POINT,SKU")
                                input_type: Option[String],
                                @ApiModelProperty(position = 12, value = "input point", required = false, example = "10")
                                input_point: Option[Int],
                                @ApiModelProperty(position = 13, value = "input eans", required = false)
                                input_eans: Option[Seq[String]],
                                @ApiModelProperty(position = 14, value = "input freecodes", required = false)
                                input_freecodes: Option[Seq[String]],
                                @ApiModelProperty(position = 15, value = "participation limit")
                                limits: Option[Seq[GameLimitRequest]]
                              )

  case class GameLimitRequest(
                               @ApiModelProperty(position = 1, value = "type of limit", required = true, example = "PARTICIPATION", allowableValues = "PARTICIPATION,WIN")
                               `type`: Option[String],
                               @ApiModelProperty(position = 2, value = "unit of limit", required = true, example = "SECOND", allowableValues = "SECOND,DAY,SESSION")
                               unit: Option[String],
                               @ApiModelProperty(position = 3, value = "value of unit", example = "1")
                               unit_value: Option[Int],
                               @ApiModelProperty(position = 4, value = "value", example = "10")
                               value: Option[Int]
                             )

  case class GameResponse(
                           @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                           id: UUID,
                           @ApiModelProperty(position = 2, value = "type", dataType = "string", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                           `type`: GameType.Value,
                           @ApiModelProperty(position = 3, value = "status", dataType = "string", required = true, example = "DRAFT", allowableValues = "DRAFT,ACTIVATE,ARCHIVED")
                           status: GameStatusType.Value,
                           @ApiModelProperty(position = 4, value = "parent game", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                           parent_id: Option[UUID] = None,
                           @ApiModelProperty(position = 5, value = "reference", required = true, example = "MY_CONTEST")
                           reference: String,
                           @ApiModelProperty(position = 6, value = "portal code", required = false, example = "WW_DANON")
                           portal_code: Option[String] = None,
                           @ApiModelProperty(position = 7, value = "title", example = "My new game")
                           title: Option[String] = None,
                           @ApiModelProperty(position = 8, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                           start_date: Instant,
                           @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                           timezone: String,
                           @ApiModelProperty(position = 10, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                           end_date: Instant,
                           @ApiModelProperty(position = 11, value = "input type", dataType = "string", required = true, example = "FREE", allowableValues = "FREE,POINT,SKU")
                           input_type: GameInputType.Value,
                           @ApiModelProperty(position = 12, value = "input point", required = false, example = "10")
                           input_point: Option[Int] = None,
                           @ApiModelProperty(position = 13, value = "input eans", required = false)
                           input_eans: Option[Seq[String]] = None,
                           @ApiModelProperty(position = 14, value = "input freecodes", required = false)
                           input_freecodes: Option[Seq[String]] = None,
                           @ApiModelProperty(position = 15, value = "participation limits")
                           limits: Seq[GameLimitResponse] = Seq.empty,
                           @ApiModelProperty(position = 16, value = "lines")
                           lines: Seq[GameLineResponse] = Seq.empty
                         )

  case class GameForListResponse(
                                  @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                                  id: UUID,
                                  @ApiModelProperty(position = 2, value = "type", dataType = "string", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                  `type`: GameType.Value,
                                  @ApiModelProperty(position = 3, value = "status", dataType = "string", required = true, example = "DRAFT", allowableValues = "DRAFT,ACTIVATE,ARCHIVED")
                                  status: GameStatusType.Value,
                                  @ApiModelProperty(position = 4, value = "parent game", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                  parent_id: Option[UUID] = None,
                                  @ApiModelProperty(position = 5, value = "reference", required = true, example = "MY_CONTEST")
                                  reference: String,
                                  @ApiModelProperty(position = 6, value = "portal code", required = false, example = "WW_DANON")
                                  portal_code: Option[String] = None,
                                  @ApiModelProperty(position = 7, value = "title", example = "My new game")
                                  title: Option[String] = None,
                                  @ApiModelProperty(position = 8, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                  start_date: Instant,
                                  @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                                  timezone: String,
                                  @ApiModelProperty(position = 10, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                  end_date: Instant,
                                  @ApiModelProperty(position = 11, value = "input type", dataType = "string", required = true, example = "FREE", allowableValues = "FREE,POINT,SKU")
                                  input_type: GameInputType.Value,
                                  @ApiModelProperty(position = 12, value = "input point", required = false, example = "10")
                                  input_point: Option[Int] = None
                                )


  case class GameLimitResponse(
                                @ApiModelProperty(position = 1, value = "type of limit", dataType = "string", required = true, example = "PARTICIPATION", allowableValues = "PARTICIPATION,WIN")
                                `type`: GameLimitType.Value,
                                @ApiModelProperty(position = 2, value = "unit of limit", dataType = "string", required = true, example = "SECOND", allowableValues = "SECOND,DAY,SESSION")
                                unit: GameLimitUnit.Value,
                                @ApiModelProperty(position = 3, value = "value of unit", example = "1")
                                unit_value: Option[Int],
                                @ApiModelProperty(position = 4, value = "value", example = "10")
                                value: Int
                              )

  /**
    * --------------------------------------------
    * Game Line
    * --------------------------------------------
    */
  case class GameLineCreateRequest(
                                    @ApiModelProperty(position = 1, value = "prize id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                                    prize_id: Option[UUID],
                                    @ApiModelProperty(position = 2, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                    start_date: Option[Instant],
                                    @ApiModelProperty(position = 3, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                    end_date: Option[Instant],
                                    @ApiModelProperty(position = 4, value = "quantity", example = "10")
                                    quantity: Option[Int]
                                  )


  case class GameLineListRequest(uc: UserContext, gameId: UUID)


  case class GameLineResponse(
                               @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                               id: UUID,
                               @ApiModelProperty(position = 2, value = "prize id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                               prize_id: UUID,
                               @ApiModelProperty(position = 3, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                               start_date: Instant,
                               @ApiModelProperty(position = 4, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                               end_date: Instant,
                               @ApiModelProperty(position = 5, value = "quantity", example = "10")
                               quantity: Int
                             )


  implicit object GameType extends Enumeration {
    val Instant: GameType.Value = Value("INSTANT")
    val Draw: GameType.Value = Value("DRAW")

    val all = Seq(Instant, Draw)

    def withNameOptional(name: String): Option[GameType.Value] = try {
      Some(this.withName(name))
    } catch {
      case _: Throwable => None
    }

  }

  implicit object GameStatusType extends Enumeration {
    val Draft: GameStatusType.Value = Value("DRAFT")
    val Activated: GameStatusType.Value = Value("ACTIVATED")
    val Archived: GameStatusType.Value = Value("ARCHIVED")

    val all = Seq(Draft, Activated, Archived)

    def withNameOptional(name: String): Option[GameStatusType.Value] = try {
      Some(this.withName(name))
    } catch {
      case _: Throwable => None
    }
  }

  implicit object GameLimitType extends Enumeration {
    val Participation: GameLimitType.Value = Value("PARTICIPATION")
    val Win: GameLimitType.Value = Value("WIN")

    val all = Seq(Participation, Win)
  }

  implicit object GameLimitUnit extends Enumeration {
    val Second: GameLimitUnit.Value = Value("SECOND")
    val Day: GameLimitUnit.Value = Value("DAY")
    val Session: GameLimitUnit.Value = Value("SESSION")

    val all = Seq(Second, Day, Session)
  }

  implicit object GameInputType extends Enumeration {
    val Other: GameInputType.Value = Value("OTHER")
    val Point: GameInputType.Value = Value("POINT")
    val Pincode: GameInputType.Value = Value("PINCODE")

    val all = Seq(Other, Point, Pincode)
  }


  trait GameJsonFormats extends DefaultJsonFormats {
    implicit val gameLimitRequest: RootJsonFormat[GameLimitRequest] = jsonFormat4(GameLimitRequest)
    implicit val gameCreateRequest: RootJsonFormat[GameCreateRequest] = jsonFormat13(GameCreateRequest)
    implicit val gameUpdateRequest: RootJsonFormat[GameUpdateRequest] = jsonFormat12(GameUpdateRequest)
    implicit val gameLineCreateRequest: RootJsonFormat[GameLineCreateRequest] = jsonFormat4(GameLineCreateRequest)

    implicit val gameType: RootJsonFormat[GameType.Value] = enumFormat(GameType)
    implicit val gameInputType: RootJsonFormat[GameInputType.Value] = enumFormat(GameInputType)
    implicit val gameLimitType: RootJsonFormat[GameLimitType.Value] = enumFormat(GameLimitType)
    implicit val gameStatus: RootJsonFormat[GameStatusType.Value] = enumFormat(GameStatusType)

    implicit val gameLimitUnit: RootJsonFormat[GameLimitUnit.Value] = enumFormat(GameLimitUnit)
    implicit val gameLimitResponse: RootJsonFormat[GameLimitResponse] = jsonFormat4(GameLimitResponse)
    implicit val gameLineResponse: RootJsonFormat[GameLineResponse] = jsonFormat5(GameLineResponse)

    implicit val gameResponse: RootJsonFormat[GameResponse] = jsonFormat16(GameResponse)
    implicit val gameForListResponse: RootJsonFormat[GameForListResponse] = jsonFormat12(GameForListResponse)
  }

}

