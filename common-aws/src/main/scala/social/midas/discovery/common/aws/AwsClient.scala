/**
 * Copyright 2018 Midas Technologies AG
 */
package social.midas.discovery.common.aws

import cats.effect.IO
import java.util.concurrent.{
 CancellationException, CompletableFuture, CompletionException,
}
import java.util.function.BiFunction
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.utils.SdkAutoCloseable

/**
 * Generic abstraction of an AWS async client of type `C` with a
 * builder `B`.
 */
abstract class AwsClient[
  B <: AwsClientBuilder[B,C],
  C <: SdkAutoCloseable,
] {

  /**
   * The region the client should carry out operations in.
   */
  def region: Region

  /**
   * The builder of this client.
   */
  protected def builder: B

  private def buildClient(): C =
    builder.region(region).build()

  /**
   * Use this to do AWS SDK calls on this client.
   * 
   * It wraps the call into an [[cats.effect.IO]] which is cancelable.
   */
  def withClient[A](f: C => CompletableFuture[A]): IO[A] =
    IO.cancelable(cb => {
      val client = buildClient()

      val handler = new BiFunction[A, Throwable, Unit] {
        override def apply(result: A, err: Throwable): Unit = {
          client.close()

          err match {
            case null =>
              cb(Right(result))
            case _: CancellationException =>
              ()
            case ex: CompletionException if ex.getCause().ne(null) =>
              cb(Left(ex.getCause()))
            case ex =>
              cb(Left(ex))
          }
        }
      }

      val cf = f(client)
      cf.handle[Unit](handler)
      IO(cf.cancel(true))
    })
}