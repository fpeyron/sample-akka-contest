package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.GameEntity.{Game, GameInputType, GameLimit, GameLimitType, GameLimitUnit, GamePrize, GameStatus, GameType}
import com.betc.danon.game.utils.DefaultJsonSupport
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object GameEntity {

  case class Game(
                   id: UUID,
                   `type`: GameType.Value,
                   status: GameStatus.Value,
                   parents: Seq[UUID] = Seq.empty,
                   code: String,
                   countryCode: String,
                   title: Option[String] = None,
                   startDate: Instant,
                   timezone: String,
                   endDate: Instant,
                   inputType: GameInputType.Value,
                   inputPoint: Option[Int] = None,
                   inputEans: Seq[String] = Seq.empty,
                   inputFreecodes: Seq[String] = Seq.empty,
                   limits: Seq[GameLimit] = Seq.empty,
                   prizes: Seq[GamePrize] = Seq.empty,
                   tags: Seq[String] = Seq.empty
                 )

  case class GameLimit(
                        @ApiModelProperty(position = 1, value = "type of limit", dataType = "string", required = true, example = "PARTICIPATION", allowableValues = "PARTICIPATION,WIN")
                        `type`: GameLimitType.Value,
                        @ApiModelProperty(position = 2, value = "unit of limit", dataType = "string", required = true, example = "SECOND", allowableValues = "SECOND,DAY,SESSION")
                        unit: GameLimitUnit.Value,
                        @ApiModelProperty(position = 3, value = "value of unit", example = "1")
                        unit_value: Option[Int],
                        @ApiModelProperty(position = 4, value = "value", example = "10")
                        value: Int
                      )

  case class GamePrize(
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

  implicit object GameStatus extends Enumeration {
    val Draft: GameStatus.Value = Value("DRAFT")
    val Activated: GameStatus.Value = Value("ACTIVATED")
    val Archived: GameStatus.Value = Value("ARCHIVED")

    val all = Seq(Draft, Activated, Archived)

    def withNameOptional(name: String): Option[GameStatus.Value] = try {
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
    val Game: GameLimitUnit.Value = Value("GAME")

    val all = Seq(Second, Day, Game)
  }

  implicit object GameInputType extends Enumeration {
    val Other: GameInputType.Value = Value("OTHER")
    val Point: GameInputType.Value = Value("POINT")
    val Pincode: GameInputType.Value = Value("PINCODE")

    val all = Seq(Other, Point, Pincode)
  }


  def sortByParent(xs: List[Game]): List[Game] = {

    def less(a: Game, b: Game): Boolean = {
      if (b.parents.contains(a.id)) true
      else if (a.parents.contains(b.id)) false
      //else if (a.parent_id.isEmpty && b.parent_id.isEmpty) a.id.compareTo(b.id) > 0
      //else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get != b.parent_id.get) a.parent_id.get.compareTo(b.parent_id.get) > 0
      //else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get == b.parent_id.get) b.id.compareTo(a.id) > 0
      //else b.parent_id.getOrElse(b.id).compareTo(a.parent_id.getOrElse(a.id)) > 0
      else a.code.compareTo(b.code) < 0
    }

    def merge(xs: List[Game], ys: List[Game]): List[Game] = (xs, ys) match {
      case (Nil, _) => ys
      case (_, Nil) => xs
      case (x :: xs1, y :: ys1) =>
        if (less(x, y)) x :: merge(xs1, ys)
        else y :: merge(xs, ys1)
    }

    val n = xs.length / 2
    if (n == 0) xs
    else {
      val (ys, zs) = xs splitAt n
      merge(sortByParent(ys), sortByParent(zs))
    }
  }
}

object GameDto {

  trait GameJsonSupport extends DefaultJsonSupport {
    implicit val gameLimitRequest: RootJsonFormat[GameLimitRequest] = jsonFormat4(GameLimitRequest)
    implicit val gameCreateRequest: RootJsonFormat[GameCreateRequest] = jsonFormat13(GameCreateRequest)
    implicit val gameUpdateRequest: RootJsonFormat[GameUpdateRequest] = jsonFormat12(GameUpdateRequest)
    implicit val gamePrizeCreateRequest: RootJsonFormat[GamePrizeCreateRequest] = jsonFormat4(GamePrizeCreateRequest)

    implicit val gameType: RootJsonFormat[GameType.Value] = enumFormat(GameType)
    implicit val gameInputType: RootJsonFormat[GameInputType.Value] = enumFormat(GameInputType)
    implicit val gameLimitType: RootJsonFormat[GameLimitType.Value] = enumFormat(GameLimitType)
    implicit val gameStatus: RootJsonFormat[GameStatus.Value] = enumFormat(GameStatus)

    implicit val gameLimitUnit: RootJsonFormat[GameLimitUnit.Value] = enumFormat(GameLimitUnit)
    implicit val gameLimitResponse: RootJsonFormat[GameLimit] = jsonFormat4(GameLimit)
    implicit val gamePrizeResponse: RootJsonFormat[GamePrize] = jsonFormat5(GamePrize)

    implicit val gameResponse: RootJsonFormat[GameResponse] = jsonFormat16(GameResponse)
    implicit val gameForListResponse: RootJsonFormat[GameForListDto] = jsonFormat12(GameForListDto)
  }

  case class GameCreateRequest(
                                @ApiModelProperty(position = 1, value = "type", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                `type`: Option[String],
                                @ApiModelProperty(position = 2, value = "code", required = true, example = "MY_CONTEST")
                                code: Option[String],
                                @ApiModelProperty(position = 3, value = "parent games", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                parents: Option[Seq[UUID]] = None,
                                @ApiModelProperty(position = 4, value = "title", example = "My new game")
                                title: Option[String],
                                @ApiModelProperty(position = 5, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                start_date: Option[Instant],
                                @ApiModelProperty(position = 6, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                end_date: Option[Instant],
                                @ApiModelProperty(position = 7, value = "time zone", example = "+02:00")
                                timezone: Option[String],
                                @ApiModelProperty(position = 8, value = "input type", required = false, example = "OTHER", allowableValues = "OTHER,POINT,SKU")
                                input_type: Option[String],
                                @ApiModelProperty(position = 9, value = "input point", required = false, example = "10")
                                input_point: Option[Int],
                                @ApiModelProperty(position = 10, value = "participation limit")
                                limits: Option[Seq[GameLimitRequest]],
                                @ApiModelProperty(position = 11, value = "input eans")
                                input_eans: Option[Seq[String]] = None,
                                @ApiModelProperty(position = 12, value = "input freecodes")
                                input_freecodes: Option[Seq[String]] = None,
                                @ApiModelProperty(position = 14, value = "tags")
                                tags: Option[Seq[String]] = None
                              )

  case class GameUpdateRequest(
                                @ApiModelProperty(position = 1, value = "code", required = true, example = "MY_CONTEST")
                                code: Option[String],
                                @ApiModelProperty(position = 2, value = "parent games", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                parents: Option[Seq[UUID]] = None,
                                @ApiModelProperty(position = 4, value = "title", example = "My new game")
                                title: Option[String],
                                @ApiModelProperty(position = 5, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                start_date: Option[Instant],
                                @ApiModelProperty(position = 6, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                end_date: Option[Instant],
                                @ApiModelProperty(position = 7, value = "time zone", example = "+02:00")
                                timezone: Option[String],
                                @ApiModelProperty(position = 8, value = "input type", required = false, example = "OTHER", allowableValues = "OTHER,POINT,SKU")
                                input_type: Option[String],
                                @ApiModelProperty(position = 9, value = "input point", required = false, example = "10")
                                input_point: Option[Int],
                                @ApiModelProperty(position = 10, value = "participation limit")
                                limits: Option[Seq[GameLimitRequest]],
                                @ApiModelProperty(position = 14, value = "input eans")
                                input_eans: Option[Seq[String]] = None,
                                @ApiModelProperty(position = 15, value = "input freecodes")
                                input_freecodes: Option[Seq[String]] = None,
                                @ApiModelProperty(position = 14, value = "tags")
                                tags: Option[Seq[String]] = None
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
                           status: GameStatus.Value,
                           @ApiModelProperty(position = 4, value = "parent games", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                           parents: Option[Seq[UUID]] = None,
                           @ApiModelProperty(position = 5, value = "code", required = true, example = "MY_CONTEST")
                           code: String,
                           @ApiModelProperty(position = 6, value = "title", example = "My new game")
                           title: Option[String] = None,
                           @ApiModelProperty(position = 7, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                           start_date: Instant,
                           @ApiModelProperty(position = 8, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                           end_date: Instant,
                           @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                           timezone: String,
                           @ApiModelProperty(position = 10, value = "input type", dataType = "string", required = true, example = "OTHER", allowableValues = "OTHER,POINT,SKU")
                           input_type: GameInputType.Value,
                           @ApiModelProperty(position = 11, value = "input point", required = false, example = "10")
                           input_point: Option[Int] = None,
                           @ApiModelProperty(position = 12, value = "input eans", required = false)
                           limits: Option[Seq[GameLimit]] = None,
                           @ApiModelProperty(position = 13, value = "prizes")
                           prizes: Option[Seq[GamePrize]] = None,
                           @ApiModelProperty(position = 14, value = "input eans")
                           input_eans: Option[Seq[String]] = None,
                           @ApiModelProperty(position = 15, value = "input freecodes")
                           input_freecodes: Option[Seq[String]] = None,
                           @ApiModelProperty(position = 14, value = "tags")
                           tags: Option[Seq[String]] = None
                         ) {
    def this(r: Game) = this(id = r.id, `type` = r.`type`, status = r.status, parents = Some(r.parents).find(_.nonEmpty), code = r.code, title = r.title, start_date = r.startDate, timezone = r.timezone, end_date = r.endDate, input_type = r.inputType, input_point = r.inputPoint, limits = Some(r.limits).find(_.nonEmpty), prizes = Some(r.prizes).find(_.nonEmpty), input_eans = Some(r.inputEans).find(_.nonEmpty), input_freecodes = Some(r.inputFreecodes).find(_.nonEmpty), tags = Some(r.tags).find(_.nonEmpty))
  }

  case class GameForListDto(
                             @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                             id: UUID,
                             @ApiModelProperty(position = 2, value = "type", dataType = "string", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                             `type`: GameType.Value,
                             @ApiModelProperty(position = 3, value = "status", dataType = "string", required = true, example = "DRAFT", allowableValues = "DRAFT,ACTIVATE,ARCHIVED")
                             status: GameStatus.Value,
                             @ApiModelProperty(position = 4, value = "parent game", example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                             parents: Option[Seq[UUID]] = None,
                             @ApiModelProperty(position = 5, value = "code", required = true, example = "MY_CONTEST")
                             code: String,
                             @ApiModelProperty(position = 6, value = "title", example = "My new game")
                             title: Option[String] = None,
                             @ApiModelProperty(position = 7, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                             start_date: Instant,
                             @ApiModelProperty(position = 8, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                             end_date: Instant,
                             @ApiModelProperty(position = 9, value = "time zone", example = "+02:00")
                             timezone: String,
                             @ApiModelProperty(position = 10, value = "input type", dataType = "string", required = true, example = "POINT", allowableValues = "OTHER,POINT,SKU")
                             input_type: GameInputType.Value,
                             @ApiModelProperty(position = 11, value = "input point", required = false, example = "10")
                             input_point: Option[Int] = None,
                             @ApiModelProperty(position = 12, value = "tags")
                             tags: Option[Seq[String]] = None
                           ) {
    def this(game: Game) = this(id = game.id, `type` = game.`type`, status = game.status, parents = Some(game.parents).find(_.nonEmpty), code = game.code, title = game.title, start_date = game.startDate, timezone = game.timezone, end_date = game.endDate, input_type = game.inputType, input_point = game.inputPoint, tags = Some(game.tags).find(_.nonEmpty))
  }

  /**
    * --------------------------------------------
    * Game Prize
    * --------------------------------------------
    */
  case class GamePrizeCreateRequest(
                                     @ApiModelProperty(position = 1, value = "prize id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                                     prize_id: Option[UUID],
                                     @ApiModelProperty(position = 2, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                     start_date: Option[Instant],
                                     @ApiModelProperty(position = 3, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                     end_date: Option[Instant],
                                     @ApiModelProperty(position = 4, value = "quantity", example = "10")
                                     quantity: Option[Int]
                                   )

}

