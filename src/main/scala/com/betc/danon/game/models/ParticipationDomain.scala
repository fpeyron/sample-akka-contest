package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.GameEntity.{GameInputType, GameType}
import com.betc.danon.game.models.PrizeDao.{PrizeJsonSupport, PrizeResponse}
import com.betc.danon.game.utils.DefaultJsonSupport
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object ParticipationEntity

object ParticipationDto {

  trait PartnerJsonSupport extends DefaultJsonSupport with PrizeJsonSupport {
    implicit val customerParticipationStatusType: RootJsonFormat[ParticipationStatusType.Value] = enumFormat(ParticipationStatusType)
    implicit val customerParticipateRequest: RootJsonFormat[CustomerParticipateRequest] = jsonFormat4(CustomerParticipateRequest)
    implicit val customerParticipateResponse: RootJsonFormat[CustomerParticipateResponse] = jsonFormat4(CustomerParticipateResponse)
    implicit val customerGameResponse: RootJsonFormat[CustomerGameResponse] = jsonFormat10(CustomerGameResponse)
  }

  case class CustomerParticipateRequest(
                                         @ApiModelProperty(position = 1, value = "game code", required = true, example = "MY_CONTEST")
                                         game_code: String,
                                         @ApiModelProperty(position = 2, value = "transaction_code", required = false, example = "22345465656")
                                         transaction_code: Option[String],
                                         @ApiModelProperty(position = 3, value = "ean", required = false, example = "10")
                                         ean: Option[String],
                                         @ApiModelProperty(position = 4, value = "meta", required = false)
                                         meta: Option[Map[String, String]] = None
                                       )

  implicit object ParticipationStatusType extends Enumeration {
    val Lost: ParticipationStatusType.Value = Value("LOST")
    val Win: ParticipationStatusType.Value = Value("WIN")

    val all = Seq(Lost, Win)

    def withNameOptional(name: String): Option[ParticipationStatusType.Value] = try {
      Some(this.withName(name))
    } catch {
      case _: Throwable => None
    }

  }

  case class CustomerParticipateResponse(
                                          @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                          id: UUID,
                                          @ApiModelProperty(position = 2, value = "date", required = true, example = "2018-01-01T00:00:00.000+02:00")
                                          date: Instant,
                                          @ApiModelProperty(position = 3, value = "status", required = true, example = "OTHER", allowableValues = "REJECTED,LOST,WIN")
                                          status: ParticipationStatusType.Value,
                                          @ApiModelProperty(position = 4, value = "prize", required = false)
                                          prize: Option[PrizeResponse] = None
                                        )


  case class CustomerGameResponse(
                                   @ApiModelProperty(position = 1, value = "code", required = true, example = "MY_CONTEST")
                                   code: String,
                                   @ApiModelProperty(position = 2, value = "parent", required = false, example = "MY_PARENT")
                                   parents: Option[Seq[String]] = None,
                                   @ApiModelProperty(position = 3, value = "type", dataType = "string", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                   `type`: GameType.Value,
                                   @ApiModelProperty(position = 4, value = "title", example = "My new game")
                                   title: Option[String] = None,
                                   @ApiModelProperty(position = 5, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                   start_date: Instant,
                                   @ApiModelProperty(position = 6, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                   end_date: Instant,
                                   @ApiModelProperty(position = 7, value = "input type", dataType = "string", required = true, example = "OTHER", allowableValues = "OTHER,POINT,SKU")
                                   input_type: GameInputType.Value,
                                   @ApiModelProperty(position = 8, value = "input point", required = false, example = "10")
                                   input_point: Option[Int] = None,
                                   @ApiModelProperty(position = 9, value = "participations count", required = true, example = "10")
                                   participationCount: Int,
                                   @ApiModelProperty(position = 10, value = "win count", required = true, example = "10")
                                   instantWinCount: Int
                                 )

}