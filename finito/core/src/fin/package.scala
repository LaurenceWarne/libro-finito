package fin

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

import cats.Applicative
import cats.effect._
import cats.implicits._
import org.http4s.Response
import org.http4s.client.Client

package object service {

  def mockedClient(response: String): Client[IO] =
    Client.apply[IO](_ =>
      Resource.pure[IO, Response[IO]](
        Response[IO](body = fs2.Stream.emits(response.getBytes("UTF-8")))
      )
    )
}

/**
  * A Test clock that always returns a constant time.
  *
  * @param epoch the constant time as a unix epoch
  */
final case class TestClock[F[_]: Applicative](epoch: Long) extends Clock[F] {

  override def applicative = implicitly[Applicative[F]]

  override def realTime: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]

  override def monotonic: F[FiniteDuration] =
    FiniteDuration(epoch, MILLISECONDS).pure[F]
}
