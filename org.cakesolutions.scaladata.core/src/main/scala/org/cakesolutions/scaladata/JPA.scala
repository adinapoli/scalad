package org.cakesolutions.scaladata

import javax.persistence.EntityManager
import javax.persistence.criteria.Predicate

/**
 * @author janmachacek
 */
class JPA(private val entityManager: EntityManager) {
  require(entityManager != null, "The 'entityManager' must not be null.")

  import ScalaData._

  import scalaz._

  def get[T](id: Serializable)(implicit evidence: ClassManifest[T]) = {
    val entity = entityManager.find(evidence.erasure, id)
    if (entity != null)
      Some(entity.asInstanceOf[T])
    else
      None
  }

  def persist(entity: AnyRef) = {
    entityManager.getTransaction.begin()
    entityManager.persist(entity)
    entityManager.getTransaction.commit()

    entity
  }

  def s2[T, R](i: IterV[T, R])(implicit evidence: ClassManifest[T]) = {
    (q: QueryBuilder) =>
      val query = CriteriaWrapper.getQuery(q, entityManager, evidence.erasure)

      val r = query.getResultList.iterator().asInstanceOf[java.util.Iterator[T]]
      i(r).run
  }

  def select[T, R](i: IterV[T, R])(implicit evidence: ClassManifest[T]) = {
    val cb = entityManager.getCriteriaBuilder
    val criteriaQuery = cb.createQuery(evidence.erasure)
    val root = criteriaQuery.from(evidence.erasure)
    criteriaQuery.orderBy(cb.asc(root.get("username")))
    val query = entityManager.createQuery(criteriaQuery)

    val r = query.getResultList.iterator().asInstanceOf[java.util.Iterator[T]]
    i(r).run
  }

}

