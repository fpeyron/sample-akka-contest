package com.betc.danon.game.models

import java.util.UUID

import com.betc.danon.game.models.PrizeDomain.{Prize, PrizeType}
import com.betc.danon.game.utils.DefaultJsonSupport
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object PrizeDomain {

  case class Prize(
                    id: UUID,
                    code: String,
                    countryCode: String,
                    `type`: PrizeType.Value,
                    title: Option[String] = None,
                    label: String,
                    description: Option[String] = None,
                    picture: Option[String] = None,
                    vendorCode: Option[String] = None,
                    faceValue: Option[Int] = None,
                    points: Option[Int] = None
                  )

  implicit object PrizeType extends Enumeration {
    val Point: PrizeType.Value = Value("POINT")
    val Gift: PrizeType.Value = Value("GIFT")
    val GiftShop: PrizeType.Value = Value("GIFTSHOP")

    val all = Seq(Point, Gift, GiftShop)
  }

}

object PrizeDao {

  trait PrizeJsonSupport extends DefaultJsonSupport {
    implicit val prizeCreateRequest: RootJsonFormat[PrizeCreateRequest] = jsonFormat9(PrizeCreateRequest)

    implicit val prizeType: RootJsonFormat[PrizeType.Value] = enumFormat(PrizeType)
    implicit val prizeResponse: RootJsonFormat[PrizeResponse] = jsonFormat10(PrizeResponse)
  }

  // Service
  case class PrizeCreateRequest(
                                 @ApiModelProperty(position = 1, value = "type", required = true, example = "POINTS", allowableValues = "POINTS,GIFTSHOP,GIFT")
                                 `type`: Option[String],
                                 @ApiModelProperty(position = 1, value = "code", required = true, example = "POINT_10")
                                 code: Option[String],
                                 @ApiModelProperty(position = 3, value = "title", example = "My new Prize")
                                 title: Option[String],
                                 @ApiModelProperty(position = 4, value = "label", example = "My new label prize")
                                 label: Option[String],
                                 @ApiModelProperty(position = 5, value = "description", example = "My new description prize")
                                 description: Option[String],
                                 @ApiModelProperty(position = 6, value = "picture", example = "myPicture.jpg")
                                 picture: Option[String],
                                 @ApiModelProperty(position = 7, value = "gift vendor code", example = "VENDOR")
                                 vendor_code: Option[String],
                                 @ApiModelProperty(position = 8, value = "giftshop face value", example = "200")
                                 face_value: Option[Int],
                                 @ApiModelProperty(position = 9, value = "points", example = "200")
                                 points: Option[Int]
                               )

  case class PrizeResponse(
                            @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                            id: UUID,
                            @ApiModelProperty(position = 3, value = "code", required = true, example = "POINT10")
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
    def this(prize: Prize) = this(
      id = prize.id,
      code = prize.code,
      `type` = prize.`type`,
      title = prize.title,
      label = prize.label,
      description = prize.description,
      picture = prize.picture,
      vendor_code = prize.vendorCode,
      face_value = prize.faceValue,
      points = prize.points
    )
  }

}