package scalad.jdbc.operations

import scalad.jdbc.JDBCOperations
import java.sql.{ResultSet, PreparedStatement}
import scalad.Query

/**
 * @author janmachacek
 */
trait Iteratees extends ParameterSettter {
  this: JDBCOperations =>

  import scalaz.Enumerator
  import scalaz.IterV._
  import scalaz.IterV

  class ResultSetIterator[T](private val resultSet: ResultSet,
                             private val mapper: (ResultSet) => T) extends Iterator[T] {

    def hasNext = !resultSet.isLast

    def next() = {
      resultSet.next()
      mapper(resultSet)
    }
    
    def close() {
      println("closed")
      resultSet.close()
    }
  }

  implicit val resultSetEnumerator = new Enumerator[ResultSetIterator] {

    @scala.annotation.tailrec
    def apply[E, A](iterator: ResultSetIterator[E], i: IterV[E, A]): IterV[E, A] = i match {
      case _ if !iterator.hasNext =>
        iterator.close()
        i
      case Done(acc, input) =>
        iterator.close()
        i
      case Cont(k) =>
        val x = iterator.next()
        apply(iterator, k(El(x)))
    }
  }

  def select[R, T](query: Query, i: IterV[T, R])(mapper: ResultSet => T) = {
    val executor = (ps: PreparedStatement) => {
      val rs: ResultSet = ps.executeQuery()
      val iterator = new ResultSetIterator[T](rs, mapper)
      i(iterator).run
    }

    execute[PreparedStatement, R](_.prepareStatement(query.sql), parameterSetter(query), executor)
  }

  def select[R, T](sql: String, i: IterV[T, R])(mapper: ResultSet => T) = {
    val executor = (ps: PreparedStatement) => {
      val rs: ResultSet = ps.executeQuery()
      val iterator = new ResultSetIterator[T](rs, mapper)
      i(iterator).run
    }

    execute[PreparedStatement, R](_.prepareStatement(sql), (_=>()), executor)
  }
  
}