package scalad

import transaction.PlatformTransactionManager

/**
 * @author janmachacek
 */
object Scalad {

  import scalaz.Input
  import scalaz.IterV._
  import scalaz.Enumerator
  import scalaz.IterV

  implicit val javaIteratorEnumerator = new Enumerator[java.util.Iterator] {

    @scala.annotation.tailrec
    def apply[E, A](iterator: java.util.Iterator[E], i: IterV[E, A]): IterV[E, A] = i match {
      case _ if !iterator.hasNext => i
      case Done(acc, input) => i
      case Cont(k) =>
        val x = iterator.next
        apply(iterator, k(El(x)))
    }
  }

  /**
   * Perform the function {{f}} in with transactional semantics, i.e. run the operations in the function atomically,
   * independently, consistently and durably. Ensure that the operations are committed when {{f}} completes normally and
   * that the operations in {{f}} are rolled back when {{f}} ends with an exception.
   *
   * @param manager the transaction manager that operates the underlying resource
   * @param f the function to execute with transactional semantics
   */
  def transactionally[A](manager: PlatformTransactionManager)(f: => A) = {
    val transaction = manager.getTransaction
    try {
      transaction.begin()
      val ret = f
      transaction.commit()

      ret
    } catch {
      case e: Exception =>
        e.printStackTrace()
        transaction.rollback()
        throw e
    }
  }

  def list[T] = collect[T, List]
  def all[T] = list[T]

  def one[E]: IterV[E, E] = {
    def step(s: Input[E]): IterV[E, E] =
      s(el = {e =>
          s(el = _ => throw new RuntimeException("Too many results."),
            empty = Done(None, EOF[E]),
            eof = Done(None, EOF[E]))
          Done(e, s)},
        empty = throw new RuntimeException("No results."),
        eof = throw new RuntimeException("No results."))
    Cont(step)
  }

  /**
   * Identity of a particular record or an object. Prefer this function instead of specifying the
   * column name directly, particularly when you are not using raw JDBC.
   *
   * @return the {{Identity}} property
   */
  def id = Identity()

  implicit def toPartialRestriction(property: String) = new PartialRestriction(NamedProperty(property))
  
  implicit def toPartialRestriction(id: Identity) = new PartialRestriction(id)
  
  implicit def toQuery(restriction: Restriction) = new Query(restriction, Nil, Nil)

  implicit def toPartialOrder(property: String) = new PartialOrder(NamedProperty(property))

  implicit def toGroupBy(property: String) = GroupBy(NamedProperty(property))

}
