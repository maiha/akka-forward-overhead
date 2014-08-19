import akka.actor._
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future

case object Up

class CountActor(max: Int) extends Actor {
  var count = 0L

  override def receive = {
    case Up => count += 1
    case msg: String => sender() ! msg
  }
}

class RootActor(max: Int) extends Actor {
  val counter = context.actorOf(Props(new CountActor(max)))
  override def receive = {
    case msg => counter forward msg
  }
}

/*
 * @example {
 *   sbt -mem 4096 "run direct  10000000"
 *   sbt -mem 4096 "run forward 10000000"
 * }
 */
object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("StressTest")
    args.toList match {
      case "direct"  :: i :: Nil => run(system.actorOf(Props(new CountActor(i.toInt))), i.toInt)
      case "forward" :: i :: Nil => run(system.actorOf(Props(new RootActor (i.toInt))), i.toInt)
      case _ => println("usage: run (direct|forward) nrMax")
    }
    system.shutdown
  }

  def run(counter: ActorRef, max: Int) {
    execute(counter, max)
  }

  def execute(counter: ActorRef, max: Int) {
    val runtime = Runtime.getRuntime()
    val now = System.nanoTime

    for(i <- 1 to max) counter ! Up
    import runtime.{ totalMemory, freeMemory, maxMemory }
    var usedMb = (totalMemory - freeMemory) / 1024 / 1024
    waitActor(counter)
    val msec = (System.nanoTime - now) / 1000 / 1000

    println(s"${max/1000/1000}M $msec msec # mem: ${usedMb}MB")
  }

  def waitActor(ref: ActorRef) {
    implicit val system = ActorSystem("sniffer")
    val inbox = ActorDSL.inbox()
    inbox.send(ref, "done")
    inbox.select(10.seconds) {
      case x: String => println(s"inbox: ${x}")
    }
    system.shutdown
  }
}

