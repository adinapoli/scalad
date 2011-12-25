package scalad

import java.lang.StringBuffer


object PreparedQuery {

  def apply(rawQuery: Query)(implicit simplifier: RestrictionSimplifier) = {
    abstract case class Token(position: Int)
    case class Start() extends Token(-1)
    case class Mid(position_ : Int, text: String) extends Token(position_)
    case class End(text: String) extends Token(Int.MaxValue)
    
    class StringIterator(private[this] val s: String, private[this] val start: Int) {
      private var i = start
      
      def hasNext = i < s.length()

      def next(n: Int) {
        i = i + n
      }

      def apply() = {
        s.charAt(i)
      }
      
      def apply(n: Int) = {
        if (i + n > 0 && i < s.length() - n) Some(s.charAt(i + n)) else None
      }
      
      def position = i

      override def toString = {
        val sb = new StringBuffer(s)
        val c = sb.charAt(i)
        sb.setCharAt(i, c.toUpper)

        sb.toString
      }
    }

    val queryText: String = rawQuery.query

    def next(token: Token): Token = {
      val text = new StringBuffer
      var openString: Boolean = false
      var openEscape: Boolean = false
      val it = new StringIterator(queryText, token.position + 1)

      while (it.hasNext) {
        if (it() == '\\') {
          it(1) match {
            case Some('\\') => it.next(2)  // we have \, if next is \, nowt
            case Some('?')  => it.next(2)  // we have \, if next is ?, nowt
            case Some(':')  => it.next(2)  // we have \, if next is :, nowt
            case Some('\'') => it.next(2)  // we have \, if next is ', nowt
            case _ => openEscape = !openEscape
          }
        } 
        if (it() == '\'') {
          it(1) match {
            case Some('\'') => it.next(2)  // we have ', if next is ', nowt
            case _ => openString = !openString
          }
        }
        
        if (!openEscape && !openString) {
          if (text.length() > 0) {
            it() match {
              case '?' => return Mid(it.position, "?")
              case ' ' => return Mid(it.position, text.toString.trim())
              case '=' => return Mid(it.position, text.toString.trim())
              case '<' => return Mid(it.position, text.toString.trim())
              case '>' => return Mid(it.position, text.toString.trim())
              case '!' => return Mid(it.position, text.toString.trim())
              case '(' => return Mid(it.position, text.toString.trim())
              case ')' => return Mid(it.position, text.toString.trim())
              case _ =>
            }
          }
          text.append(it())
        }

        it.next(1)
      }
      End(text.toString.trim())
    }

    def loop(token: Token, position: Int, params: Seq[PreparedQueryParameter]): Seq[PreparedQueryParameter] = {
      next(token) match {
        case t@Mid(_, "") => loop(t, position, params)
        case t@Mid(_, text) =>
          text.charAt(0) match {
            case ':' => NamedPreparedQueryParameter(text, position) +: loop(t, position + 1, params)
            case '?' => PositionalPreparedQueryParameter(position) +: loop(t, position + 1, params)
            case _ => loop(t, position, params)
          }
        case End("") => params
        case End(text) =>
          text.charAt(0) match {
            case ':' => NamedPreparedQueryParameter(text, position) +: params
            case '?' => PositionalPreparedQueryParameter(position) +: params
            case _ => params
          }
      }
    }

    val params = loop(Start(), 0, Nil)
    var positionalQuery = queryText
    params.foreach { p =>
      p match {
        case NamedPreparedQueryParameter(name, _) => positionalQuery = positionalQuery.replace(name, "?")
        case _ =>
      }
    }

    new PreparedQuery(positionalQuery, params,
      simplifier.simplifyRestriction(rawQuery.restriction),
      rawQuery.orderByClauses, rawQuery.groupByClauses)
  }

}

class PreparedQuery private(private[scalad] val query: String,
                            private[scalad] val parameters: Iterable[PreparedQueryParameter],
                            private[scalad] val restriction: Restriction,
                            private[scalad] val orderByClauses: List[OrderBy],
                            private[scalad] val groupByClauses: List[GroupBy]) {


}

abstract case class PreparedQueryParameter()
case class PositionalPreparedQueryParameter(position: Int) extends PreparedQueryParameter
case class NamedPreparedQueryParameter(name: String, position: Int) extends PreparedQueryParameter