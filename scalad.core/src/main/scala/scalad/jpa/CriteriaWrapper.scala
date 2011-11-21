package scalad.jpa

import javax.persistence.EntityManager
import scalad._
import javax.persistence.criteria.{Predicate, JoinType}

/**
 * @author janmachacek
 */
object CriteriaWrapper {

  def getQuery[T](query: JPAQuery, entityManager: EntityManager, entityType: Class[T]) = {
    val cb = entityManager.getCriteriaBuilder
    val criteriaQuery = cb.createQuery(entityType)
    val root = criteriaQuery.from(entityType)

    def getIdProperty = {
      val entity = entityManager.getMetamodel.entity(entityType)
      val singularAttributes = entity.getSingularAttributes.iterator()
      var id: Option[String] = None
      while (singularAttributes.hasNext && id != None) {
        val singularAttribute = singularAttributes.next()
        if (singularAttribute.isId) id = Some(singularAttribute.getName)
      }

      id
    }

    def getExpression[T](property: Property) = property match {
      case NamedProperty(name) => root.get[T](name)
      case Identity() => root.get[T](getIdProperty.get)
    }

    query.joins.foreach {join =>
      (join.eager, join.inner) match {
        case (true, true) => root.fetch(join.path, JoinType.INNER)
        case (true, false) => root.fetch(join.path)
        case (false, true) => root.join(join.path, JoinType.INNER)
        case (false, false) => root.join(join.path)
      }
    }

    criteriaQuery.distinct(true)
    
    criteriaQuery.orderBy(query.orderByClauses.map(_ match {
                  case Asc(property) => cb.asc(getExpression(property))
                  case Desc(property) => cb.desc(getExpression(property))
                }).toArray:_*)
    criteriaQuery.groupBy(query.groupByClauses.map(g => getExpression(g.property)).toArray:_*)
    
    def getPredicate(restriction: Restriction): Option[Predicate] = restriction match {
        case Binary(p, '==, v) => Some(cb.equal(getExpression(p), v))
        case Binary(p, '!=, v) => Some(cb.notEqual(getExpression(p), v))
        case Like(p, v)        => Some(cb.like(getExpression[String](p), v))
        //case Binary(p, '>, v)  => Some(cb.greaterThan(getExpression(p), v))
        //case Binary(p, '<, v)  => Some(cb.lessThan(getExpression(p), v))
        //case Binary(p, '>=, v) => Some(cb.greaterThanOrEqualTo(getExpression(p), v))
        //case Binary(p, '<=, v) => Some(cb.lessThanOrEqualTo(getExpression(p), v))
        //case In(p, v)          => cb.isMember(getExpression(p), v)
        case Disjunction(lhs, rhs) => Some(cb.or(Array(getPredicate(lhs).get, getPredicate(rhs).get):_*))
        case Conjunction(lhs, rhs) => Some(cb.and(Array(getPredicate(lhs).get, getPredicate(rhs).get):_*))
        case Tautology() => None
        case Contradiction() => None
        case Skip() => None
      }
    
    criteriaQuery.where(Array(getPredicate(query.restriction).get):_*)

    val q = entityManager.createQuery(criteriaQuery)
    
    query.pageOption match {
      case Some(Page(start, max)) => 
        q.setFirstResult(start)
        q.setMaxResults(max)
      case _ =>
    }
    
    q
  }

}
