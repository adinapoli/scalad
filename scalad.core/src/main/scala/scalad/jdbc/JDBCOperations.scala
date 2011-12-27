package scalad.jdbc

import java.sql.{ResultSet, Statement, Connection}


trait JDBCOperations {
  type StatementCreator[S <: Statement] = (Connection) => S
  type StatementSetter[S <: Statement] = (S) => Unit
  type StatementExecutor[S <: Statement, R] = (S) => R
  type ResultSetMapper[R] = (ResultSet) => R

  def perform[S <: Statement, R](statementCreator: StatementCreator[S],
           statementSetter: StatementSetter[S],
           statementExecutor: StatementExecutor[S, R]): R

}
