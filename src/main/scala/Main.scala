import akka.actor._
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

case object Up
case object Done
case object State
case class Counted(num: Long)

class CountActor extends Actor {
  var count = 0L
  var done  = false

  override def receive = {
    case Up    => count += 1
    case Done  => done = true
    case State if done == true => sender() ! Done
    case State => // nop
  }
}

class RootActor extends Actor {
  val counter = context.actorOf(Props[CountActor])
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
      case "direct"  :: i :: Nil => run(system.actorOf(Props[CountActor]), i.toInt)
      case "forward" :: i :: Nil => run(system.actorOf(Props[RootActor ]), i.toInt)
      case _ => println("usage: run (direct|forward) nrMax")
    }
    system.shutdown
    system.awaitTermination(60.seconds)
  }

  def run(counter: ActorRef, max: Int) {
    val msec = time { execute(counter, max) }
    report(max, msec)
  }

  def report(max: Int, msec: Long) {
    println(s"${max/1000/1000}M $msec msec")
  }

  def execute(counter: ActorRef, max: Int) {
    for(i <- 1 to max) counter ! Up
    counter ! Done

    implicit val timeout = akka.util.Timeout(100)
    var done = false

    var i = 0
    while (i < 1000 && !done) {
      val future = counter.ask(State)
      future onSuccess {
        case Done =>
          done = true
        case _ =>
      }
    }
  }

  private def time[A](a: => A): Long = {
    val now = System.nanoTime
    val result = a
    val msec = (System.nanoTime - now) / 1000 / 1000
    return msec
  }
}

