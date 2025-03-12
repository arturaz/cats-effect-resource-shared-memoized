/*
 * Copyright 2025 Artūras Šlajus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect.resource_shared_memoized

import cats.effect.{Deferred, IO, Resource}
import cats.syntax.all.*
import munit.CatsEffectSuite

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

class ResourceSharedMemoizedTest extends CatsEffectSuite {
  case class State(allocations: AtomicInteger, releases: AtomicInteger, resource: Resource[IO, Unit]) {
    def memoized(releaseDelay: Option[FiniteDuration]): IO[State] = {
      releaseDelay.fold(resource.memoizeShared)(resource.memoizeSharedWithDelayedRelease).map(r => copy(resource = r))
    }

    def teardown: IO[Unit] = IO {
      val allocations = this.allocations.get()
      val releases = this.releases.get()
      assert(allocations == releases, s"allocations=$allocations != releases=$releases")
    }
  }
  object State {
    def make: IO[State] = for {
      allocations <- IO(new AtomicInteger(0))
      releases <- IO(new AtomicInteger(0))
      resource =
        Resource
          .make(IO(allocations.addAndGet(1)).void)(_ => IO(releases.addAndGet(1)).void)
    } yield apply(allocations, releases, resource)
  }

  def fixture(releaseDelay: Option[FiniteDuration]): FunFixture[State] = FunFixture.async[State](
    setup = { _ => State.make.flatMap(_.memoized(releaseDelay)).unsafeToFuture() },
    teardown = _.teardown.unsafeToFuture()
  )

  val fixture: FunFixture[State] = fixture(None)

  def cancellable(releaseDelay: Option[FiniteDuration]): FunFixture[(State, Deferred[IO, Unit])] =
    FunFixture.async[(State, Deferred[IO, Unit])](
      setup = { _ =>
        val io = for {
          deferred <- Deferred[IO, Unit]
          deferredResource = Resource.eval(deferred.get)
          state <- State.make
            .map(state => state.copy(resource = deferredResource *> state.resource))
            .flatMap(_.memoized(releaseDelay))
        } yield (state, deferred)
        io.unsafeToFuture()
      },
      teardown = _._1.teardown.unsafeToFuture()
    )

  val cancellable: FunFixture[(State, Deferred[IO, Unit])] = cancellable(None)

  fixture.test("it should only allocate once") { state =>
    state.resource.use { _ =>
      state.resource.use { _ =>
        IO(assertEquals(state.allocations.get(), 1))
      }
    }
  }

  fixture.test("it should only release once") { state =>
    state.resource.use { _ =>
      state.resource.use { _ =>
        IO.unit
      }
    } *> IO(assertEquals(state.releases.get(), 1))
  }

  fixture.test("it should reallocate after all usages are released and then allocated again") { state =>
    val useOnce = state.resource.use { _ =>
      state.resource.use { _ =>
        IO.unit
      }
    }

    useOnce *> useOnce *> IO {
      assertEquals(state.allocations.get(), 2)
      assertEquals(state.releases.get(), 2)
    }
  }

  cancellable.test("plays nice with cancellation") { case (state, deferred) =>
    for {
      fiber <- state.resource.use(_ => IO.unit).start
      _ <- IO.sleep(250.millis)
      _ <- IO(assertEquals(state.allocations.get(), 0))
      _ <- fiber.cancel
      _ <- IO(assertEquals(state.allocations.get(), 0))
      _ <- IO(assertEquals(state.releases.get(), 0))
      _ <- deferred.complete(()).void
      _ <- state.resource.use(_ => state.resource.use(_ => IO.unit))
      _ <- IO(assertEquals(state.allocations.get(), 1))
      _ <- IO(assertEquals(state.releases.get(), 1))
    } yield ()
  }

  fixture(Some(200.millis)).test("keepAfterRelease: should wait before releasing after last user stops using it") {
    state =>
//    for {
//      _ <-
//    }
  }
}
