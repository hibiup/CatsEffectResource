package org.hibiup.resource

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.FlatSpec

class EffectResourceTest extends FlatSpec with StrictLogging{

    "Cat Effect Resource" should "" in {
        import cats.effect.{IO, Resource}
        /**
          * Reource 的 use 方法是最基本的入口函数，它的签名如下：
          *
          *     def use[B](f: A => F[B])(implicit F: Bracket[F, Throwable]): F[B]
          *
          * (隐式 Bracket 是 MonadError 的扩展。参考：https://typelevel.org/cats-effect/typeclasses/bracket.html)
          *
          * 通过签名可以看到 Resource 有一个 use 函数，它接受一个函数参数，这个函数参数定义了如何使用一个资源。
          * use 的参数 A 作为输入资源的类型，然后通过执行容器 F 来使用参数并返回 B。也就是说，假设我们输入一个
          * 字符串资源 “World”，然后打印 “Hello, World”，我们使用的执行容器为IO，于是容器和执行过程为：
          * `IO(println("Hello," + A))`，返回 Unit, 因此返回类型是 IO[Unit]
          *
          * 1) 因此在使用 Resource 的时候首先需要将执行容器和执行过程定义为 String => IO[Unit] 函数：
          * */
        val greet: String => IO[Unit] = x => IO(println("Hello " ++ x))

        /**
          * 2) Resource 是抽象函数，它不能直接初始化，我们需要通过 Resource.liftF 函数来生成 Resource 实例，
          * 但是实例初始化只包含输入资源。liftF 的参数就是我们希望传递给这个 Resource 的资源，当然我们也必须将
          * 它放在对齐的容器中，于是得到：
          * */
        val resource: Resource[IO, String] = Resource.liftF(IO.pure("World"))

        /**
          * 3) 第二步只是准备好了 Resource 的输入资源，并没有和我们之前定义的函数联系起来，接下来我们通过 use
          * 将参数传递给 greet 完成二者的绑定:
          * */
        val done = resource.use(greet)

        /**
          * 4) 很显然 use 返回容器 F，在这个例子中也就是 IO，因此我们可以执行它：
          *
          * 以上 2,3,4 整个过程可以一行写完：
          *   Resource.liftF(IO.pure("World")).use(greet).unsafeRunSync
          * */
        done.unsafeRunSync
    }

    "Resource acquire and release" should "" in {
        /**
          * 一般来说“资源”都具有“获取”(acquire)和“释放”(release)两个过程，make 函数接受两个函数参数来
          * 获取并在使用结束后安全地关闭资源。它的签名如下：
          *
          *     def make[F[_], A](acquire: F[A])(release: A => F[Unit])(implicit F: Functor[F]): Resource[F, A]
          * */
        import cats.effect.{IO, Resource}
        import cats.implicits._

        /**
          * 1) 定义获取和释放函数：
          *
          * 获取函数与 liftF 的作用基本上是一样的，只不过它要求允许用户自己完成输入值的容器打包：
          * */
        val acquire: IO[String] = IO(println("Acquire cats:")) *> IO("cats")

        /**
          * 释放函数的输入值是获取函数的输出容器的签名类型 A ，输出为 Unit
          * */
        val release: String => IO[Unit] = x => IO(println(s"Release: $x"))  // “x” 是 acquire 的返回值，不会因为 evalMap 修改了输入值而改变。

        /**
          * 2) 生成带有释放方法的资源容器：
          * */
        val resource = Resource.make(acquire)(release)

        /**
          * 3) 接下来我们可以直接使用 use 将资源赋予函数。不仅如此，我们还可以在这之前使用 evalMap 方法在不离开
          * Resource 容器的情况下对资源做更多操作。比如我们定义一个 addDogs 方法修改资源，然后再将修改过的资源
          * 传递给 use 的函数:
          * */
        val addDogs: String => IO[String] = x => IO(println("...add a dog...")) *> IO.pure(x ++ " and dogs")
        val greet: String => IO[Unit] = x => IO(println(s"Hello, $x!"))  // 注意：x == cats and dogs

        resource
            .evalMap(addDogs)
            .use(greet)
            /**
              * 3-1) guarantee 相当于 final, 它在资源释放后执行 finalizer，我们可以在这里做一些额外的清扫工作
              * */
            .guarantee(IO(logger.info("Application stopped"))).unsafeRunSync
    }

    "More then one resource" should "" in {
        import cats.effect.{IO, Resource}
        import cats.implicits._

        /** 一个make resource 的通用函数 */
        def mkResource(s: String): Resource[IO, String] = {
            val acquire = IO(println(s"Acquiring $s")) *> IO.pure(s)
            def release(s: String) = IO(println(s"Releasing $s"))
            Resource.make(acquire)(release)
        }

        /**
          * 1) 假设我们有多个 Resource，可以通过 for-comprehension 合并成一个
          * */
        val r = for {
            outer <- mkResource("outer")
            inner <- mkResource("inner")
        } yield (outer, inner)

        /**
          * 2) 然后应用于 use 函数。
          *
          * 注意：执行完成后对资源的释放顺序和获取的顺序是恰好相反的。
          * */
        r.use{
            case (a, b) => IO(println(s"Using $a and $b"))
        }.unsafeRunSync
    }

    "Auto-closable Resource" should "" in {import cats.effect._
        /**
          * 对于具有自动销毁（close）能力的资源，比如 Source（具有 close 方法）：
          * */
        val acquire = IO {
            // 生成 iterable Source object，包含 "Hello" 和 "world" 两个字符串
            scala.io.Source.fromString("Hello world")
        }

        /**
          * 通过 fromAutoCloseable 获取的资源会在释放的时候自动调用 close() 方法。
          * */
        Resource.fromAutoCloseable(acquire).use(source => IO{
            println(source.mkString)
        }).unsafeRunSync()
    }

    "" should "" in {
        import java.io._
        import cats.effect._

        /**
          * 1) 打开外部文件
          *
          * Sync 由 Cats effect 隐式提供，它的参数是执行容器，比如IO。Sync含有 suspend 等系列方法用来管理容器的嵌套堆栈
          * effect（trampolining）。详细参考：https://typelevel.org/cats-effect/typeclasses/sync.html
          * */
        def reader[F[_]](file: File)(implicit sync: Sync[F]): Resource[F, BufferedReader] =
            Resource.fromAutoCloseable(
                /**
                  * delay 等效于 apply，但是它与 suspend 一起构成对 trampolining 的支持.
                  * 本例中我们将返回一个包含在 Sync[F[_]] 中的 BufferedReader
                  * */
                sync.delay {
                    new BufferedReader(new FileReader(file))
                }
            )

        /**
          * 2) 从外部文件获得输入，并不断打印出内容：
          * */
        def dumpResource[F[_]](res: Resource[F, BufferedReader])(implicit sync: Sync[F]): F[Unit] = {
            /**
              * 2-1) 循环打印。注意使用 suspend 来 flatten Resource 容器。
              * */
            def loop(in: BufferedReader): F[Unit] =
                sync.suspend {
                    val line = in.readLine()
                    if (line != null) {
                        System.out.println(line)
                        loop(in)
                    } else {
                        sync.unit
                    }
                }

            /** 3) 将外部输入作用于打印循环 */
            res.use(loop)
        }

        /** compose */
        def dumpFile[F[_]](file: File)(implicit sync: Sync[F]): F[Unit] = dumpResource(reader(file))
    }
}
