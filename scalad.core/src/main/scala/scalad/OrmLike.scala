package scalad

/**
 * Trait to be mixed-in to ORM-like implementations
 *
 * @author markh
 */
trait OrmLike {

  private def toProperty(selectable: Selectable) = selectable match {
    case SelectableField(field) => NamedProperty(field.getName)
    case SelectableSetter(setter) => NamedProperty(setter.getName)
  }
  
  implicit def toOrmQuery(q: Query) = new OrmQuery(q.restriction, q.orderByClauses, q.groupByClauses, None, Nil)

  implicit def toOrmQuery(r: Restriction) = new OrmQuery(r, Nil, Nil, None, Nil)

  implicit def toPath(expression: String) = new PartialPath(expression)
  
  implicit def toPartialRestriction2(selectable: Selectable) = new PartialRestriction(toProperty(selectable))
  
  implicit def toPartialOrder2(selectable: Selectable) = new PartialOrder(toProperty(selectable))

  implicit def toGroupBy2(selectable: Selectable) = GroupBy(toProperty(selectable))

  implicit def toPartialRestriction(property: String) = new PartialRestriction(NamedProperty(property))

  implicit def toPartialRestriction(id: Identity) = new PartialRestriction(id)

  implicit def toPartialOrder(property: String) = new PartialOrder(NamedProperty(property))

  implicit def toGroupBy(property: String) = GroupBy(NamedProperty(property))

}