package cats.effect.resource_shared_memoized

import cats.Applicative
import cats.effect._
import cats.effect.std.AtomicCell
import cats.effect.syntax.all._
import cats.syntax.all._

import scala.concurrent.duration._

object ResourceSharedMemoized {

  /** Takes a [[Resource]] and returns a [[Resource]] that will allocate the resource once, even if you use it multiple
    * times. It keeps track of how many users it has and releases the [[Resource]] when there are no more users.
    */
  def memoize[F[_]: Concurrent, A](resource: Resource[F, A]): F[Resource[F, A]] =
    memoize(resource, keepAfterRelease = None)

  /** Takes a [[Resource]] and returns a [[Resource]] that will allocate the resource once, even if you use it multiple
    * times. It keeps track of how many users it has and releases the [[Resource]] when there are no more users.
    *
    * Keeps the resource around for `keepAfterRelease` when the last user stops using it before releasing.
    */
  def memoizeWithDelayedRelease[F[_]: Temporal, A](
      resource: Resource[F, A],
      keepAfterRelease: FiniteDuration
  ): F[Resource[F, A]] =
    if (keepAfterRelease == Duration.Zero) memoize(resource)
    else memoize(resource, keepAfterRelease = Some((keepAfterRelease, Temporal[F])))

  private def memoize[F[_]: Concurrent, A](
      resource: Resource[F, A],
      keepAfterRelease: Option[(FiniteDuration, Temporal[F])]
  ): F[Resource[F, A]] = {
    def acquire(poll: Poll[F], cell: AtomicCell[F, Option[Allocated[F, A]]]) = cell.evalModify {
      case None =>
        // Allocate the resource.
        for {
          // Use `poll` to allow the resource allocation to be cancellable, for example in case `resource` is trying to
          // get a lock and can't acquire it.
          tpl <- poll(resource.allocatedCase)
          (a, cleanup) = tpl
        } yield {
          val data = ResourceSharedMemoized.Allocated(users = 1, value = a, cleanup = cleanup, removalTimer = None)
          (data.some, a)
        }

      case Some(data) =>
        // Register that we have a user.
        data.addUser.map(data => (data.some, data.value))
    }

    def cleanup(cell: AtomicCell[F, Option[Allocated[F, A]]], a: A, exitCase: Resource.ExitCase) = {
      def onNone[R]: F[R] =
        // This should never happen.
        Concurrent[F].raiseError(
          new IllegalStateException(s"Tried to release a resource that was not allocated for value '$a'")
        )

      cell.evalUpdate {
        case None => onNone
        case Some(data) =>
          data.removeUser match {
            case Some(data) => data.some.pure
            case None =>
              keepAfterRelease match {
                case None => data.cleanup(exitCase).as(None)
                case Some((keepAfterRelease, temporal)) =>
                  val timerIO = for {
                    _ <- temporal.sleep(keepAfterRelease)
                    _ <- cell.evalUpdate {
                      case None       => onNone
                      case Some(data) => data.cleanup(exitCase).as(None)
                    }
                  } yield ()

                  timerIO.start.map(data.onRemovalTimerScheduled(_).some)
              }
          }
      }
    }

    for {
      _ <- Concurrent[F].raiseWhen(keepAfterRelease.exists(_._1 <= Duration.Zero))(
        new IllegalArgumentException(s"keepAfterRelease must be > 0, but was $keepAfterRelease")
      )
      cell <- AtomicCell[F].of(Option.empty[Allocated[F, A]])
    } yield Resource.makeCaseFull[F, A](acquire(_, cell))(cleanup(cell, _, _))
  }

  /** An allocated value. */
  private case class Allocated[F[_]: Applicative, A](
      users: Long,
      value: A,
      cleanup: Resource.ExitCase => F[Unit],
      removalTimer: Option[Fiber[F, Throwable, Unit]]
  ) {
    if (removalTimer.isDefined)
      assert(users == 0, s"users must be == 0 when removalTimer is `Some`, but was $users for $value")
    else assert(users > 0, s"users must be > 0 when removalTimer is `None`, but was $users for $value")

    def addUser: F[Allocated[F, A]] = {
      removalTimer match {
        // Cancel the removal timer if one exists.
        case Some(timer) => timer.cancel.as(copy(users = users + 1, removalTimer = None))
        case None        => copy(users = users + 1).pure
      }
    }

    def removeUser: Option[Allocated[F, A]] =
      if (users == 1) None else Some(copy(users = users - 1))

    def onRemovalTimerScheduled(timer: Fiber[F, Throwable, Unit]): Allocated[F, A] =
      copy(users = 0, removalTimer = Some(timer))
  }
}
