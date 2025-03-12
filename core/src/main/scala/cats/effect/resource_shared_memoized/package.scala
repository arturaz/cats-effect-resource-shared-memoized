package cats.effect

import scala.concurrent.duration.FiniteDuration

package object resource_shared_memoized {
  implicit class ResourceSharedMemoizedOps[F[_], A](private val resource: Resource[F, A]) extends AnyVal {

    /** Takes a [[Resource]] and returns a [[Resource]] that will allocate the resource once, even if you use it
      * multiple times. It keeps track of how many users it has and releases the [[Resource]] when there are no more
      * users.
      *
      * @see
      *   [[ResourceSharedMemoized.memoize]]
      */
    def memoizeShared(implicit F: Concurrent[F]): F[Resource[F, A]] =
      ResourceSharedMemoized.memoize(resource)

    /** Takes a [[Resource]] and returns a [[Resource]] that will allocate the resource once, even if you use it
      * multiple times. It keeps track of how many users it has and releases the [[Resource]] when there are no more
      * users.
      *
      * Keeps the resource around for `keepAfterRelease` when the last user stops using it before releasing.
      *
      * @see
      *   [[ResourceSharedMemoized.memoizeWithDelayedRelease]]
      */
    def memoizeSharedWithDelayedRelease(keepAfterRelease: FiniteDuration)(implicit F: Temporal[F]): F[Resource[F, A]] =
      ResourceSharedMemoized.memoizeWithDelayedRelease(resource, keepAfterRelease)
  }
}
