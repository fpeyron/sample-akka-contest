package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.PrizeDao.{PrizeJsonSupport, PrizeResponse}
import com.betc.danon.game.utils.DefaultJsonSupport
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object ParticipationEntity

object ParticipationDto {

  trait PartnerJsonSupport extends DefaultJsonSupport with PrizeJsonSupport {
    implicit val participationStatusType: RootJsonFormat[ParticipationStatusType.Value] = enumFormat(ParticipationStatusType)
    implicit val participateRequest: RootJsonFormat[ParticipateRequest] = jsonFormat4(ParticipateRequest)
    implicit val participateResponse: RootJsonFormat[ParticipateResponse] = jsonFormat4(ParticipateResponse)
  }

  case class ParticipateRequest(
                                 @ApiModelProperty(position = 1, value = "game code", required = true, example = "MY_CONTEST")
                                 game_code: Option[String],
                                 @ApiModelProperty(position = 2, value = "transaction_code", required = false, example = "22345465656")
                                 transaction_code: Option[String],
                                 @ApiModelProperty(position = 3, value = "ean", required = false, example = "10")
                                 ean: Option[String],
                                 @ApiModelProperty(position = 4, value = "metadata", required = false)
                                 metadata: Option[Map[String, String]] = None
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

  case class ParticipateResponse(
                                  @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                  id: UUID,
                                  @ApiModelProperty(position = 2, value = "date", required = true, example = "2018-01-01T00:00:00.000+02:00")
                                  date: Instant,
                                  @ApiModelProperty(position = 3, value = "status", required = true, example = "OTHER", allowableValues = "REJECTED,LOST,WIN")
                                  status: ParticipationStatusType.Value,
                                  @ApiModelProperty(position = 4, value = "prize", required = false)
                                  prize: Option[PrizeResponse] = None
                                )

}