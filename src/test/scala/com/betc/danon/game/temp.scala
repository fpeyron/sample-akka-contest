package com.betc.danon.game

object temp {


  case class Game(id: String, parent_id:Option[String]) extends Ordered[Game] {

    def compare(that: Game): Int =
    if (that.parent_id.isDefined && that.parent_id.get == this.id) 1
    else if (this.parent_id.isDefined && this.parent_id.get == that.id) -1
    // else if (this.parent_id.isEmpty && this.parent_id.isEmpty) this.id.compareTo(that.id)
      // else if (this.parent_id.isDefined && that.parent_id.isDefined && this.parent_id.get != that.parent_id.get) - this.parent_id.get.compareTo(that.parent_id.get)
    else this.parent_id.getOrElse(this.id).compareTo(that.parent_id.getOrElse(that.id))
  }

  val myList =List(
    Game(id="AAA", parent_id=Some("CCC")),
    Game(id="BBB", parent_id=None),
    Game(id="DDD", parent_id=None),
    Game(id="EEE", parent_id=Some("CCC")),
    Game(id="CCC", parent_id = Some("BBB")))

  myList.sorted

  def less2(a: Game, b: Game): Int = {
    if (b.parent_id.isDefined && b.parent_id.get == a.id) 1
    else if (a.parent_id.isDefined && a.parent_id.get == b.id) -1
    else if (a.parent_id.isEmpty && b.parent_id.isEmpty) a.id.compareTo(b.id)
    else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get != b.parent_id.get) a.parent_id.get.compareTo(b.parent_id.get)
    else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get == b.parent_id.get) b.id.compareTo(a.id)
    else b.parent_id.getOrElse(b.id).compareTo(a.parent_id.getOrElse(a.id))
  }



  def less(a: Game, b: Game): Boolean = {
  if (b.parent_id.isDefined && b.parent_id.get == a.id) true
    else if (a.parent_id.isDefined && a.parent_id.get == b.id) false
    else if (a.parent_id.isEmpty && b.parent_id.isEmpty) a.id > b.id
    else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get != b.parent_id.get) a.parent_id.get > b.parent_id.get
    else if (a.parent_id.isDefined && b.parent_id.isDefined && a.parent_id.get == b.parent_id.get) a.id < b.id
    else a.parent_id.getOrElse(a.id) < b.parent_id.getOrElse(b.id)
  }


  def msort[Game](less: (Game, Game) => Boolean)(xs: List[Game]): List[Game] = {

    def merge(xs: List[Game], ys: List[Game]): List[Game] =
      (xs, ys) match {
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
      merge(msort(less)(ys), msort(less)(zs))
    }
  }
  msort(less)(myList)
}
