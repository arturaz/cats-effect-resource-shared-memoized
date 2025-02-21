# Shared Resource Cache for Cats Effect

Micro-library that provides a way to memoize `Resource[F, A]` so they would be shared between accesses.

The resource is allocated once when the first consumer uses it and is deallocated when the last consumer stops
using it.

## Motivation

The main use-case for developing this was sharing resources like database connections between test suites.

For example:
```scala mdoc
import cats.effect.*
import cats.effect.resource_shared_memoized.*
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.global

// A fake database connection
class DatabaseConnection extends AutoCloseable {
  override def close() = {} 
}

object MySuite {
  val database = ResourceSharedMemoized.memoize(
    Resource.fromAutoCloseable(IO(new DatabaseConnection))
  ).unsafeRunSync()
}

class MyTests extends munit.CatsEffectSuite {
  val dbFixture = ResourceFunFixture(MySuite.database)

  dbFixture.test("test 1") { db =>
    // The `db` object here is shared with...
  }

  dbFixture.test("test 2") { db =>
    // The `db` object here. 
  }
}
```

This allows you to run one test case, a suite of tests, or all tests and not have to worry about acquiring or releasing 
the database connection.

### Can't we just use `.memoize`?

Unfortunately no. `memoize` returns `Resource[F, Resource[F, A]]`, and we need `Resource[F, A]`. If we use
`.allocated` on the outer resource then we need to perform the cleanup ourselves, which `ResourceSharedMemoized`
handles for you.

Take a look at this example:
```scala mdoc
case class State(acquires: Int, releases: Int, users: Int) {
  def acquire = copy(acquires = acquires + 1, users = users + 1)

  def release = copy(releases = releases + 1, users = users - 1)

  override def toString: String = s"[acquires=$acquires, releases=$releases, users=$users]"
}

val (memoizedResource, memoizedResourceState) = (for {
  state <- Ref[IO].of(State(0, 0, 0))
  resource =  Resource.make(state.modify { s =>
    val newState = s.acquire
    (newState, newState)
  })(_ => state.update(_.release))
  resource <- resource.memoize.allocated.map(_._1)
} yield (resource, state)).unsafeRunSync()

memoizedResource.use { value =>
  memoizedResourceState.get.map(state => s"\nstate: $state\nvalue: $value")
}.unsafeRunSync()

// The resource is NOT deallocated when the last consumer stops using it.
memoizedResourceState.get.unsafeRunSync()

memoizedResource.use { value1 =>
  memoizedResourceState.get.flatMap { state1 =>
    memoizedResource.use { value2 =>
      memoizedResourceState.get.map { state2 =>
        // All values should be the same
        s"\nstate1: $state1\nvalue1: $value1\nstate2: $state2\nvalue2: $value2"
      }
    }
  }
}.unsafeRunSync()

// The resource is NOT deallocated when the last consumer stops using it.
memoizedResourceState.get.unsafeRunSync()
```

We want to use the `Resource[F, A]` transparently, without caring about managing lifetimes for it.

## Usage

There is a single function `ResourceSharedMemoized.memoize` that takes a `Resource[F, A]` and returns `Resource[F, A]`.

An `.memoizeShared` extension method is also provided for `Resource[F, A]` in the 
`cats.effect.resource_shared_memoized.ResourceSharedMemoizedOps` implicit class.

The example below demonstrates on how the library works:

```scala mdoc
import cats.effect.resource_shared_memoized.*

val (cachedResource, state) = (for {
  state <- Ref[IO].of(State(0, 0, 0))
  resource =  Resource.make(state.modify { s =>
    val newState = s.acquire
    (newState, newState)
  })(_ => state.update(_.release))
  resource <- resource.memoizeShared
} yield (resource, state)).unsafeRunSync()

cachedResource.use { value =>
  state.get.map(state => s"\nstate: $state\nvalue: $value")
}.unsafeRunSync()

// The resource is deallocated when the last consumer stops using it.
state.get.unsafeRunSync()

cachedResource.use { value1 =>
  state.get.flatMap { state1 =>
    cachedResource.use { value2 =>
      state.get.map { state2 =>
        // All values should be the same
        s"\nstate1: $state1\nvalue1: $value1\nstate2: $state2\nvalue2: $value2"
      }
    }
  }
}.unsafeRunSync()

// The resource is deallocated when the last consumer stops using it.
state.get.unsafeRunSync()
```

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += 
  "io.github.arturaz" %% "cats-effect-resource-shared-memoized" % "@VERSION@"
```

Or `build.sc` if you are using [mill](https://mill-build.com):

```scala
override def ivyDeps = Agg(
  ivy"io.github.arturaz::cats-effect-resource-shared-memoized:@VERSION@"
)
```

The code from `main` branch can be obtained with:
```scala
resolvers ++= Resolver.sonatypeOssRepos("snapshots")
libraryDependencies += 
  "io.github.arturaz" %% "cats-effect-resource-shared-memoized" % "@SNAPSHOT_VERSION@"
```

For [mill](https://mill-build.com):
```scala
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      coursier.Repositories.sonatype("snapshots")
    )
  }

override def ivyDeps = Agg(
  ivy"io.github.arturaz::cats-effect-resource-shared-memoized:@SNAPSHOT_VERSION@"
)
```

## Credits

Artūras Šlajus (https://github.com/arturaz)