package org.hibiup.resource

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import cats.Applicative
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import com.typesafe.scalalogging.{Logger, StrictLogging}
import org.scalatest.FlatSpec

import scala.concurrent.Future

object AkkaAsResource extends IOApp with StrictLogging
{
    /**
      * 通过 Resource 管理 Akka system
      * */
    val resources: Resource[IO, (ActorSystem, ActorMaterializer)] = for {
        system <- Resource.make(IO(ActorSystem("demo-system"))){ s => IO(s.terminate()) }
        mat <- Resource.make(IO(ActorMaterializer()(system))){ m => IO(m.shutdown()) }
    } yield (system, mat)

    /**
      * Resource 的执行函数
      * */
    def execution(source:Source[Int, NotUsed])(resource: (ActorSystem, ActorMaterializer)): IO[_] = {
        implicit val (system, mat) = resource

        /**
          * 用 Akka stream 管理执行流程
          * */
        val process = source
            .via(Flow[Int].dropWhile(_ < 5))
            .toMat(Sink.foreach{
                i => {
                  logger.info(s"$i")
                }   // 2. run in Akka default dispatcher(fromFuture 切换了 CS)
            })(Keep.right)
            .run()

        /**
          * IOApp 提供了一个缺省的 ContextShift (ioapp-compute)，如果需要可以隐式获得。这个隐式也帮助 fromFuture  从 Akka 中
         * 切换回 IO 的 dispatcher
          *
          *   val cs = implicitly[ContextShift[IO]]
          * */
        IO{
            //Thread.sleep(1000)
            logger.info("Application started")   //1. Run in main dispatcher(最初的 dispatcher)
        } *> IO.fromFuture(IO(process)) *>
          IO.shift  // shift 确保在低版本 cats effect 中也能从 Akka system dispatcher 中切换回来。（参考下面的测试案例）
    }

    override def run(args: List[String]): IO[ExitCode] =
        resources.use(execution(Source(List(1, 2, 3, 4, 5, 6))))
            .guarantee(IO{logger.info("Application stopped")})  // 3. 回到 IOApp provides dispatcher
            .as(ExitCode.Success)
}

class AkkaAsResourceTest extends FlatSpec{
    "Akka as resource" should "" in {
        AkkaAsResource.run(List.empty[String]).unsafeRunSync()
    }

    "buggy?" should "" in {
        /**
         * https://github.com/typelevel/cats-effect/issues/444
         * */
        import cats.implicits._

        import scala.concurrent.ExecutionContext.Implicits.global
        implicit val cs = IO.contextShift(global)
        val logger = Logger(this.getClass)

        val system = ActorSystem("demo-system")

        (IO.fromFuture(IO {
            Future {
                Thread.sleep(1000)
                logger.info("In Future: Job is running in Akka dispatcher")
            }(system.getDispatcher)   // 假设这是一个 Actor 任务，它将会被运行在 Akka system dispatcher 中
        }) *>
            /**
             * 在 1.6.0 及之前版本的 IO EFFECT 中，fromFuture 不会自动切换回当前 dispatcher，因此需要手工执行 shift
             * 来完成切换以保证回到当前线程环境。但是后续版本的 fromFuture 添加了一个隐式 CS 参数来尝试执行完后回到当前
             * 线程环境（如果提供了当前环境的隐式变量，比如本例的 cs）
             * */
            IO.shift *>  // 尝试在 1.6.0 之前版本中注释掉此行就会看到无法从 Akka dispatcher 中返回的现象。
            IO(logger.info("Out of Future: Supposed to switch back to default dispatcher"))
        ).unsafeRunSync()
    }
}
