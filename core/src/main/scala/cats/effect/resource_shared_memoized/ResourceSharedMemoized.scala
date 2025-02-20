package cats.effect.resource_shared_memoized

import cats.effect.Concurrent
import cats.effect.Poll
import cats.effect.Resource
import cats.effect.std.AtomicCell
import cats.syntax.all._

object ResourceSharedMemoized {

  /** Takes a [[Resource]] and returns a [[Resource]] that will allocate the resource once, even if you use it multiple
    * times. It keeps track of how many users it has and releases the [[Resource]] when there are no more users.
    */
  def memoize[F[_]: Concurrent, A](resource: Resource[F, A]): F[Resource[F, A]] = {
    def acquire(poll: Poll[F], cell: AtomicCell[F, Option[Allocated[F, A]]]) = cell.evalModify {
      case None =>
        // Allocate the resource.
        (for {
          // Use `poll` to allow the resource allocation to be cancellable, for example in case `resource` is trying to
          // get a lock and can't acquire it.
          tpl <- poll(resource.allocated)
          (a, cleanup) = tpl
        } yield {
          val data = ResourceSharedMemoized.Allocated.make(value = a, cleanup = cleanup)
          (data.some, a)
        })
      case Some(data) =>
        // Register that we have a user.
        (data.addUser.some, data.value).pure
    }

    def cleanup(cell: AtomicCell[F, Option[Allocated[F, A]]], a: A) = cell.evalUpdate {
      case None =>
        // This should never happen.
        Concurrent[F].raiseError(
          new IllegalStateException(s"Tried to release a resource that was not allocated for value '$a'")
        )
      case Some(data) =>
        data.removeUser match {
          case Some(data) => data.some.pure
          case None       => data.cleanup.as(None)
        }
    }

    for {
      cell <- AtomicCell[F].of(Option.empty[Allocated[F, A]])
    } yield Resource.makeFull[F, A](acquire(_, cell))(cleanup(cell, _))
  }

  /** An allocated value. */
  private case class Allocated[F[_], A](users: Long, value: A, cleanup: F[Unit]) {
    assert(users > 0, s"users must be > 0, but was $users for $value")

    def addUser: Allocated[F, A] = copy(users = users + 1)

    def removeUser: Option[Allocated[F, A]] =
      if (users == 1) None else Some(copy(users = users - 1))
  }
  private object Allocated {
    def make[F[_], A](value: A, cleanup: F[Unit]): Allocated[F, A] =
      apply(users = 1, value, cleanup)
  }
}
