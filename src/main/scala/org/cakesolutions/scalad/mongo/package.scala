package org.cakesolutions.scalad.mongo

import scala.collection._
import com.mongodb._
import java.util.logging.Logger

/** These implicits make the MongoDB API nicer to use, for example by allowing
  * JSON search queries to be passed instead of `DBObject`s.
  */
object Implicits {
  import scala.language.implicitConversions

  implicit var JSON2DBObject = (json: String) => util.JSON.parse(json).asInstanceOf[DBObject]
}

/** Makes `java.util.logging` available as a `log` field. */
trait J2SELogging {
  protected lazy val log = Logger.getLogger(getClass.getName)
}

/** Mechanism for finding an entry in the database
  * which matches a query built from an archetype entity.
  */
trait IdentityQueryBuilder[T] {
  def createIdQuery(entity: T): DBObject
}

/** Mechanism for finding an entry in the database
  * which matches the query built up from a key.
  */
trait KeyQueryBuilder[T, K] {
  def createKeyQuery(key: K): DBObject
}

/** Mechanism for converting to/from Scala types and MongoDB `DBObject`s.
  *
  * Unfortunately, the actual signatures needs to be `Object` so that
  * "primitive" types (`String`, `java.lang.Long`, etc) are supported.
  */
trait MongoSerializer[T] {
  /** Only to be used when the entity is known to serialise non-trivially. */
  def serializeDB(entity: T) = serialize(entity).asInstanceOf[DBObject]

  def serialize(entity: T): Object

  def deserialize(dbObject: Object): T
}

/** Access to a MongoDB `DBCollection`.
  * Here is a good place to add an index.
  */
trait CollectionProvider[T] {
  def getCollection: DBCollection
}

/** Provides CRUD access to a MongoDB collection using client-provided implicits to:
  *
  * 1. provide the backing MongoDB `DBCollection`.
  * 2. serialise/deserialise the MongoDB representation.
  * 3. provide a concept of identity for UPDATE/DELETE operations.
  * 4. provide a concept of a key for READ operations.
  *
  * MongoDB adds an internal `_id` field to every object that is persisted in the
  * database. It is bad practice to use this `_id` field as the MongoDB documentation
  * notes it is possible it may change under highly distributed circumstances.
  *
  * ALl methods throw [[com.mongodb.MongoException]] if something bad happened that we
  * didn't expect (e.g. I/O or config).
  *
  * @author Sam Halliday
  * @author Jan Machacek
  * @see <a href="http://www.cakesolutions.net/teamblogs/2012/11/05/crud-options/">Thinking notes on the API design</a>
  */
class MongoCrud extends MongoCreate
with MongoSearch
with MongoSelectiveSearch
with MongoUpdate
with MongoDelete
with MongoRead
with MongoFind
with MongoCreateOrUpdate

// enables cross-instance concurrent DB indexing
protected object IndexedCollectionProvider {

  // synchronized access only
  private val indexed = new mutable.WeakHashMap[DBCollection, Boolean]()

  // true if the calling thread has privileged access to
  // create indexes on the collection. Such callers should
  // proceed immediately to build the indexes as it is possible
  // that no other thread will be granted such privilege.
  def privilegedIndexing(collection: DBCollection): Boolean = indexed.synchronized {
    indexed.put(collection, true) match {
      case Some(_) => false
      case None => true
    }
  }
}


/** Easy way to add unique indexes to a Mongo collection. */
trait IndexedCollectionProvider[T] extends CollectionProvider[T] with J2SELogging {

  doIndex()

  def doIndex() {
    import Implicits._
    if (IndexedCollectionProvider.privilegedIndexing(getCollection)) {
      log.info("Ensuring indexes exist on " + getCollection)
      uniqueFields.foreach(field => getCollection.ensureIndex(field, null, true))
      indexFields.foreach(field => getCollection.ensureIndex(field, null, false))
    }
  }

  /** `String`s containing the JSON definition of the index to build. */
  protected def uniqueFields: List[String] = Nil

  /** `String`s containing the JSON definition of the unique index to build. */
  protected def indexFields: List[String] = Nil
}


/** Provides a `read` query that resembles SQL's ``SELECT a WHERE a.field = ...``.
  *
  * The key must not require any special serialisation.
  */
trait FieldQueryBuilder[T, K] extends KeyQueryBuilder[T, K] {
  def createKeyQuery(key: K): DBObject = new BasicDBObject(field, key)

  def field: String
}

/** Provides a concept of identity that resembles a SQL `field` column
  *
  * The key must not require any special serialisation.
  */
trait FieldIdentityQueryBuilder[T, K] extends IdentityQueryBuilder[T] {
  def createIdQuery(entity: T): DBObject = new BasicDBObject(field, id(entity))

  def field: String

  def id(entity: T): K
}

/** Syntactic sugar for [[org.cakesolutions.scalad.mongo.FieldQueryBuilder]]. */
class StringFieldQuery[T](val field: String) extends FieldQueryBuilder[T, String]

/** Syntactic sugar for [[org.cakesolutions.scalad.mongo.FieldQueryBuilder]]. */
class LongFieldQuery[T](val field: String) extends FieldQueryBuilder[T, Long]

/** Provides a `read` query using serialised fields. */
class SerializedFieldQueryBuilder[T, K](val field: String)
                                       (implicit serialiser: MongoSerializer[K])
  extends FieldQueryBuilder[T, K] {
  override def createKeyQuery(key: K): DBObject = new BasicDBObject(field, serialiser.serialize(key))
}

/** Provides a concept of identity that resembles a SQL `field` column,
  * with serialization on the field.
  */
abstract class SerializedIdentityQueryBuilder[T, K](val field: String)
                                                   (implicit serialiser: MongoSerializer[K])
  extends FieldIdentityQueryBuilder[T, K] {
  override def createIdQuery(entity: T) = new BasicDBObject(field, serialiser.serialize(id(entity)))
}
