# Shared Resource Cache for Cats Effect

Micro-library that provides a way to memoize `Resource[F, A]` so they would be shared between accesses.

The resource is allocated once when the first consumer uses it and is deallocated when the last consumer stops
using it.

## Usage

```scala mdoc
import cats.effect.*
import cats.effect.resource_shared_memoized.*
import cats.effect.unsafe.implicits.global

val (cachedResource, resourceUsers) = (for {
  counter <- Ref[IO].of(0)
  resource <- ResourceSharedMemoized.memoize(
    Resource.make(
      counter.modify(c => (c + 1, c + 1))
    )(_ => counter.update(_ - 1))
  )
} yield (resource, counter)).unsafeRunSync()

cachedResource.use { value =>
  resourceUsers.get.map(counter => s"counter: $counter, value=$value")
}.unsafeRunSync()

cachedResource.use { value1 =>
  resourceUsers.get.flatMap { counter1 =>
    cachedResource.use { value2 =>
      resourceUsers.get.map { counter2 =>
        // All values should be 1
        s"counter1: $counter1, value1=$value1, counter2: $counter2, value2=$value2"
      }
    }
  }
}.unsafeRunSync()

// The resource is deallocated when the last consumer stops using it.
resourceUsers.get.unsafeRunSync()
```

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "io.github.arturaz" %% "yantl" % "@VERSION@"
```

Or `build.sc` if you are using [mill](https://mill-build.com):

```scala
override def ivyDeps = Agg(
  ivy"io.github.arturaz::yantl:@VERSION@"
)
```

The code from `main` branch can be obtained with:
```scala
resolvers ++= Resolver.sonatypeOssRepos("snapshots")
libraryDependencies += "io.github.arturaz" %% "yantl" % "@SNAPSHOT_VERSION@"
```

For [mill](https://mill-build.com):
```scala
  override def repositoriesTask = T.task {
    super.repositoriesTask() ++ Seq(
      coursier.Repositories.sonatype("snapshots")
    )
  }

override def ivyDeps = Agg(
  ivy"io.github.arturaz::yantl:@SNAPSHOT_VERSION@"
)
```

## Credits

Artūras Šlajus (https://github.com/arturaz)