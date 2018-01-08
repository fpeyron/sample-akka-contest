package fr.sysf.sample.models

import java.util.UUID

import fr.sysf.sample.DefaultJsonFormats
import io.swagger.annotations.ApiModelProperty
import spray.json.RootJsonFormat

object Prize {


  // Service
  case object PrizeListRequest

  case class PrizeGetRequest(id: UUID)

  case class PrizeCreateRequest(
                                 @ApiModelProperty(position = 1, value = "type", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                                 `type`: Option[String],
                                 @ApiModelProperty(position = 2, value = "title", example = "My new Prize")
                                 title: Option[String],
                                 @ApiModelProperty(position = 3, value = "label", example = "My new label prize")
                                 label: Option[String],
                                 @ApiModelProperty(position = 4, value = "description", example = "My new description prize")
                                 description: Option[String],
                                 @ApiModelProperty(position = 5, value = "gift vendor code", example = "VENDOR")
                                 vendor_code: Option[String],
                                 @ApiModelProperty(position = 6, value = "giftshop face value", example = "200")
                                 face_value: Option[Int]
                               )

  case class PrizeResponse(
                            @ApiModelProperty(position = 1, value = "id", required = true, example = "1c637dce-ebf0-11e7-8c3f-9a214cf093ae")
                            id: UUID,
                            @ApiModelProperty(position = 2, value = "type", required = true, example = "INSTANT", allowableValues = "INSTANT,DRAW")
                            `type`: PrizeType.Value,
                            @ApiModelProperty(position = 3, value = "title", example = "My new Prize")
                            title: Option[String],
                            @ApiModelProperty(position = 4, value = "label", example = "My new label prize")
                            label: String,
                            @ApiModelProperty(position = 5, value = "description", example = "My new description prize")
                            description: Option[String],
                            @ApiModelProperty(position = 6, value = "gift vendor code", example = "VENDOR")
                            vendor_code: Option[String],
                            @ApiModelProperty(position = 7, value = "giftshop face value", example = "200")
                            face_value: Option[Int]
                          )

  implicit object PrizeType extends Enumeration {
    val Point: PrizeType.Value = Value("POINT")
    val Gift: PrizeType.Value = Value("GIFT")
    val GiftShop: PrizeType.Value = Value("GIFTSHOP")

    val all = Seq(Point, Gift, GiftShop)
  }

  trait PrizeJsonFormats extends DefaultJsonFormats {
    implicit val prizeCreateRequest: RootJsonFormat[PrizeCreateRequest] = jsonFormat6(PrizeCreateRequest)

    implicit val prizeType: RootJsonFormat[PrizeType.Value] = enumFormat(PrizeType)
    implicit val prizeResponse: RootJsonFormat[PrizeResponse] = jsonFormat7(PrizeResponse)
  }

}