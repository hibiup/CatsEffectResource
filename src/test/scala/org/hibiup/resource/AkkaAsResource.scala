package org.hibiup.resource

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.typesafe.scalalogging.{Logger, StrictLogging}
import org.scalatest.FlatSpec

object AkkaAsResource extends IOApp with StrictLogging
{
    /**
      * 通过 Resource 管理 Akka system
      * */
    val resources: Resource[IO, (ActorSystem, ActorMaterializer)] = for {
        system <- Resource.make {
            IO(ActorSystem("demo-system"))
        } { s => IO(s.terminate()) }
        mat <- Resource.make {
            IO(ActorMaterializer()(system))
        } { m => IO(m.shutdown()) }
    } yield (system, mat)

    /**
      * Resource 的执行函数
      * */
    def execution(source:Source[Int, NotUsed])(resource: (ActorSystem, ActorMaterializer)): IO[_] = {
        implicit val (system, mat) = resource

        /**
          * 用 Akka stream 管理执行流程
          * */
        val output = source
            .via(Flow[Int].dropWhile(_ < 5))
            .toMat(Sink.foreach{
                i => logger.info(s"$i")   // 2. run in Akka default dispatcher(fromFuture 切换了 CS)
            })(Keep.right)
            .run()


        /**
          * IOApp 提供一个缺省的 ContextShift (ioapp-compute)，如果需要可以隐式获得
          *
          *   val cs = implicitly[ContextShift[IO]]
          * */
        IO{
            //Thread.sleep(1000)
            logger.info("Application started")   //1. Run in main dispatcher(最初的 dispatcher)
        } *> IO.fromFuture(IO(output))
    }

    override def run(args: List[String]): IO[ExitCode] =
        resources.use(execution(Source(List(1, 2, 3, 4, 5, 6))))
            .guarantee(IO(logger.info("Application stopped")))  // 3. 回到 IOApp provides dispatcher
            .as(ExitCode.Success)
}

class AkkaAsResourceTest extends FlatSpec{
    "Akka as resource" should "" in {
        AkkaAsResource.run(List.empty[String]).unsafeRunSync()
    }
}
