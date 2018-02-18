package com.betc.danon.game.models

import java.time.Instant
import java.util.UUID

import com.betc.danon.game.models.GameEntity.{GameInputType, GameType}
import com.betc.danon.game.models.PrizeDao.PrizeJsonSupport
import com.betc.danon.game.models.PrizeDomain.{Prize, PrizeType}
import com.betc.danon.game.utils.DefaultJsonSupport
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object ParticipationEntity

object ParticipationDto {

  trait PartnerJsonSupport extends DefaultJsonSupport with PrizeJsonSupport {
    implicit val customerParticipationStatusType: RootJsonFormat[ParticipationStatus.Value] = enumFormat(ParticipationStatus)
    implicit val customerParticipateRequest: RootJsonFormat[CustomerParticipateRequest] = jsonFormat4(CustomerParticipateRequest)
    implicit val customerPrizeResponse: RootJsonFormat[CustomerPrizeResponse] = jsonFormat9(CustomerPrizeResponse)
    implicit val customerParticipateResponse: RootJsonFormat[CustomerParticipateResponse] = jsonFormat5(CustomerParticipateResponse)
    implicit val customerGameResponse: RootJsonFormat[CustomerGameResponse] = jsonFormat14(CustomerGameResponse)
    implicit val customerConfirmParticipationRequest: RootJsonFormat[CustomerConfirmParticipationRequest] = jsonFormat1(CustomerConfirmParticipationRequest)
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

  implicit object ParticipationStatus extends Enumeration {
    val lost: ParticipationStatus.Value = Value("LOST")
    val toConfirm: ParticipationStatus.Value = Value("TOCONFIRM")
    val pending: ParticipationStatus.Value = Value("PENDING")
    val win: ParticipationStatus.Value = Value("WIN")
    val all = Seq(lost, pending, toConfirm, win)

    def withNameOptional(name: String): Option[ParticipationStatus.Value] = try {
      Some(this.withName(name))
    } catch {
      case _: Throwable => None
    }

  }

  case class CustomerParticipateResponse(
                                          @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                          id: UUID,
                                          @ApiModelProperty(position = 2, value = "game_code", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093aa")
                                          game_code: String,
                                          @ApiModelProperty(position = 3, value = "date", required = true, example = "2018-01-01T00:00:00.000+02:00")
                                          date: Instant,
                                          @ApiModelProperty(position = 4, value = "status", required = true, example = "OTHER", allowableValues = "REJECTED,LOST,WIN")
                                          status: ParticipationStatus.Value,
                                          @ApiModelProperty(position = 5, value = "prize", required = false)
                                          prize: Option[CustomerPrizeResponse] = None
                                        )

  case class CustomerPrizeResponse(
                                    @ApiModelProperty(position = 1, value = "code", required = true, example = "POINT_20")
                                    code: String,
                                    @ApiModelProperty(position = 2, value = "type", dataType = "string", required = true, example = "POINTS", allowableValues = "POINTS,GIFTSHOP,GIFT")
                                    `type`: PrizeType.Value,
                                    @ApiModelProperty(position = 3, value = "title", example = "My new Prize")
                                    title: Option[String] = None,
                                    @ApiModelProperty(position = 4, value = "label", example = "My new label prize")
                                    label: String,
                                    @ApiModelProperty(position = 5, value = "description", example = "My new description prize")
                                    description: Option[String] = None,
                                    @ApiModelProperty(position = 5, value = "picture", example = "myPicture.jpg")
                                    picture: Option[String] = None,
                                    @ApiModelProperty(position = 7, value = "gift vendor code", example = "VENDOR")
                                    vendor_code: Option[String] = None,
                                    @ApiModelProperty(position = 8, value = "giftshop face value", example = "200")
                                    face_value: Option[Int] = None,
                                    @ApiModelProperty(position = 9, value = "points", example = "200")
                                    points: Option[Int]
                                  ) {
    def this(prize: Prize) = this(code = prize.code, `type` = prize.`type`, title = prize.title, label = prize.label, description = prize.description, picture = prize.picture, vendor_code = prize.vendorCode, face_value = prize.faceValue, points = prize.points)
  }


  case class CustomerGameResponse(
                                   @ApiModelProperty(position = 1, value = "code", required = true, example = "MY_CONTEST")
                                   code: String,
                                   @ApiModelProperty(position = 2, value = "parent", required = false, example = "MY_PARENT")
                                   parents: Option[Seq[String]] = None,
                                   @ApiModelProperty(position = 3, value = "type", dataType = "string", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                   `type`: GameType.Value,
                                   @ApiModelProperty(position = 4, value = "title", example = "My new game")
                                   title: Option[String] = None,
                                   @ApiModelProperty(position = 5, value = "picture", example = "myPicture.png")
                                   picture: Option[String] = None,
                                   @ApiModelProperty(position = 6, value = "description", example = "my description \n and detail ...")
                                   description: Option[String] = None,
                                   @ApiModelProperty(position = 7, value = "start date", example = "2018-01-01T00:00:00.000+02:00")
                                   start_date: Instant,
                                   @ApiModelProperty(position = 8, value = "end date", example = "2018-02-01T23:59:59.999+02:00")
                                   end_date: Instant,
                                   @ApiModelProperty(position = 9, value = "input type", dataType = "string", required = true, example = "OTHER", allowableValues = "OTHER,POINT,SKU")
                                   input_type: GameInputType.Value,
                                   @ApiModelProperty(position = 10, value = "input point", required = false, example = "10")
                                   input_point: Option[Int] = None,
                                   @ApiModelProperty(position = 11, value = "participations count", required = true, example = "10")
                                   participation_count: Int,
                                   @ApiModelProperty(position = 12, value = "win count", required = true, example = "10")
                                   instant_win_count: Int,
                                   @ApiModelProperty(position = 13, value = "toconfirm count", required = true, example = "1")
                                   instant_toconfirm_count: Int,
                                   @ApiModelProperty(position = 14, value = "availability", dataType = "string", required = true, example = "AVAILABLE", allowableValues = "AVAILABLE,UNAVAILABLE_LIMIT,UNAVAILABLE_DEPENDENCY")
                                   availability: CustomerGameAvailability.Value
                                 )

  case class CustomerConfirmParticipationRequest(
                                                  @ApiModelProperty(position = 1, value = "meta", required = false)
                                                  meta: Option[Map[String, String]] = None
                                                )

  implicit object CustomerGameAvailability extends Enumeration {
    val available: CustomerGameAvailability.Value = Value("AVAILABLE")
    val unavailableLimit: CustomerGameAvailability.Value = Value("UNAVAILABLE_LIMIT")
    val unavailableDependency: CustomerGameAvailability.Value = Value("UNAVAILABLE_DEPENDENCY")
    val all = Seq(available, unavailableLimit, unavailableDependency)
  }


  def sortByParent(xs: Seq[CustomerGameResponse]): Seq[CustomerGameResponse] = {

    def mergeWithChild(parent: CustomerGameResponse): Seq[CustomerGameResponse] = {
      parent :: xs.filter(g => g.parents.getOrElse(Seq.empty).contains(parent.code)).sortBy(_.code).map(mergeWithChild).foldLeft(List.empty[CustomerGameResponse])(_ ++ _)
    }

    xs.filter(_.parents.getOrElse(Seq.empty).isEmpty).sortBy(_.code).map(mergeWithChild).foldLeft(Seq.empty[CustomerGameResponse])(_ ++ _)
  }

}