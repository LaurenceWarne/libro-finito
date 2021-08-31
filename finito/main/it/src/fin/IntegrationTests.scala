package fin

import cats.effect._
import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait
import weaver._
import fin.config.ServiceConfig

object IntegrationTests extends IOSuite {

  override type Res = Container
  override def sharedResource: Resource[IO, Container] =
    Resource.make(
      for {
        container <- IO(
          GenericContainer(
            "finito-main",
            exposedPorts = Seq(ServiceConfig.defaultPort),
            waitStrategy = Wait
              .forHttp("/version")
              .forPort(ServiceConfig.defaultPort)
          )
        )
        _ <- IO(container.start())
      } yield container
    )(container => IO(container.stop))

  test("createCollection, updateCollection") {
    for {
      _ <- IO.unit
    } yield success
  }
}
