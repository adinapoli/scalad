package scalad.jdbc

import javax.sql.DataSource
import java.sql.{Statement, Connection}

class JDBC(private val dataSource: DataSource) extends JDBCOperations {
  type ConnectionOperation[R] = (Connection) => R

  def withConnection[R](operation: ConnectionOperation[R]): R = {
    val connection = dataSource.getConnection
    try {
      operation(connection)
    } finally {
      connection.commit()
      connection.close()
    }
  }    
  
//  def apply[R](operation: ConnectionOperation[R]): R = withConnection(operation)

  /**
   * Core operation
   */
  def perform[S <: Statement, R](statementCreator: StatementCreator[S],
           statementSetter: StatementSetter[S],
           statementExecutor: StatementExecutor[S, R]): R =
    withConnection {c =>
      val preparedStatement = statementCreator(c)
      statementSetter(preparedStatement)
      statementExecutor(preparedStatement)
    }

}
