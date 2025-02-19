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

import cats.effect.{IO, Resource, SyncIO}
import munit.CatsEffectSuite

import java.util.concurrent.atomic.AtomicInteger

class ResourceSharedMemoizedTest extends CatsEffectSuite {
  class State {
    val allocations = new AtomicInteger(0)
    val releases = new AtomicInteger(0)

    val resource: Resource[IO, Unit] =
      Resource
        .make(IO(allocations.addAndGet(1)).void)(_ => IO(releases.addAndGet(1)).void)
        .memoizeShared
        .unsafeRunSync()
  }

  val fixture = FunFixture[State](
    setup = { _ => new State },
    teardown = { state =>
      val allocations = state.allocations.get()
      val releases = state.releases.get()
      assert(allocations == releases, s"allocations=$allocations != releases=$releases")
    }
  )

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
}
