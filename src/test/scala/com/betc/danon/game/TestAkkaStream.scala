package com.betc.danon.game

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.stage._

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.Random


object TestAkkaStream extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  case class Game(id: Int, name: String, limit: Option[String] = None) {
    override def hashCode: Int = id
  }


  val sampleFlow = Flow[Game].conflateWithSeed(Seq(_)) {
    case (acc, elem) => acc :+ elem
    case (acc, _) => acc
  }


  val result = Source(1 to 21)
    //.map(i => Game(id = i / 3, name = Random.nextString(5)))
    .map(i => Game(id = i, name = Random.nextString(5)))
    .via(new Duplicator)
    //.map(r => (r, Random.nextString(6)))
    .via(new AccumulateWhileUnchanged(_.id))
    //.via(new TwoBuffer)
    //.via(new FilterStage_.Filter(_ % 2 < 10))
    //.via(new DuplicatorShape_.Duplicator())
    //.via(new MapShape_.Map[Int, Int](_ / 2))

    .runWith(Sink.foreach(println))

  system.terminate()
}


class Duplicator[A] extends GraphStage[FlowShape[A, A]] {

  val in: Inlet[A] = Inlet[A]("Duplicator.in")
  val out: Outlet[A] = Outlet[A]("Duplicator.out")

  val shape: FlowShape[A, A] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      // Again: note that all mutable state
      // MUST be inside the GraphStageLogic
      var lastElem: Option[A] = None

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          lastElem = Some(elem)
          push(out, elem)
        }

        override def onUpstreamFinish(): Unit = {
          if (lastElem.isDefined) emit(out, lastElem.get)
          complete(out)
        }

      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (lastElem.isDefined) {
            push(out, lastElem.get)
            lastElem = None
          } else {
            pull(in)
          }
        }
      })
    }
}

class FirstValue[A] extends GraphStageWithMaterializedValue[FlowShape[A, A], Future[A]] {

  val in: Inlet[A] = Inlet[A]("FirstValue.in")
  val out: Outlet[A] = Outlet[A]("FirstValue.out")

  val shape: FlowShape[A, A] = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[A]) = {
    val promise = Promise[A]()
    val logic = new GraphStageLogic(shape) {

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          promise.success(elem)
          push(out, elem)

          // replace handler with one just forwarding
          setHandler(in, new InHandler {
            override def onPush(): Unit = {
              push(out, grab(in))
            }
          })
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })

    }

    (logic, promise.future)
  }
}

class TwoBuffer[A] extends GraphStage[FlowShape[A, A]] {

  val in: Inlet[A] = Inlet[A]("TwoBuffer.in")
  val out: Outlet[A] = Outlet[A]("TwoBuffer.out")

  val shape: FlowShape[A, A] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      val buffer: mutable.Queue[A] = mutable.Queue[A]()

      def bufferFull: Boolean = buffer.lengthCompare(100) == 0

      var downstreamWaiting = false

      override def preStart(): Unit = {
        // a detached stage needs to start upstream demand
        // itself as it is not triggered by downstream demand
        pull(in)
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          buffer.enqueue(elem)
          if (downstreamWaiting) {
            downstreamWaiting = false
            val bufferedElem = buffer.dequeue()
            push(out, bufferedElem)
          }
          if (!bufferFull) {
            pull(in)
          }
        }

        override def onUpstreamFinish(): Unit = {
          if (buffer.nonEmpty) {
            // emit the rest if possible
            emitMultiple(out, buffer.toIterator)
          }
          completeStage()
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if (buffer.isEmpty) {
            downstreamWaiting = true
          } else {
            val elem = buffer.dequeue
            push(out, elem)
          }
          if (!bufferFull && !hasBeenPulled(in)) {
            pull(in)
          }
        }
      })
    }

}

final class AccumulateWhileUnchanged[E, P](propertyExtractor: E => P) extends GraphStage[FlowShape[E, Seq[E]]] {

  val in: Inlet[E] = Inlet[E]("AccumulateWhileUnchanged.in")
  val out: Outlet[Seq[E]] = Outlet[Seq[E]]("AccumulateWhileUnchanged.out")

  override def shape: FlowShape[E, Seq[E]] = FlowShape.of(in, out)

  override def createLogic(attributes: Attributes): GraphStageLogic {
    //val buffer: mutable.Builder[E, Vector[E]]

    //override def postStop(): Unit

    //var currentState: Option[P]
  } = new GraphStageLogic(shape) {

    private var currentState: Option[P] = None
    private val buffer = Vector.newBuilder[E]

    setHandlers(in, out, new InHandler with OutHandler {

      override def onPush(): Unit = {
        val nextElement = grab(in)
        val nextState = propertyExtractor(nextElement)

        if (currentState.isEmpty || currentState.contains(nextState)) {
          buffer += nextElement
          pull(in)
        } else {
          val result = buffer.result()
          buffer.clear()
          buffer += nextElement
          push(out, result)
        }

        currentState = Some(nextState)
      }

      override def onPull(): Unit = {
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        val result = buffer.result()
        if (result.nonEmpty) {
          emit(out, result)
        }
        completeStage()
      }
    })

    override def postStop(): Unit = {
      buffer.clear()
    }
  }
}
