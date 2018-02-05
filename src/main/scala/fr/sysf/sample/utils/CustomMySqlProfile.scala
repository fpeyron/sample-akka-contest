package fr.sysf.sample.utils

import java.sql.{PreparedStatement, ResultSet}

import slick.ast._
import slick.jdbc.MySQLProfile

object CustomMySqlProfile extends MySQLProfile {

  import java.util.UUID

  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val uuidJdbcType: UUIDJdbcType = new UUIDJdbcType {
      override def sqlTypeName(sym: Option[FieldSymbol]): String = "VARCHAR(36)"

      override def valueToSQLLiteral(value: UUID): String = s"'$value'"

      override def hasLiteralForm: Boolean = true

      override def setValue(v: UUID, p: PreparedStatement, idx: Int): Unit = p.setString(idx, toString(v))

      override def getValue(r: ResultSet, idx: Int): UUID = fromString(r.getString(idx))

      override def updateValue(v: UUID, r: ResultSet, idx: Int): Unit = r.updateString(idx, toString(v))

      private def toString(uuid: UUID): String = if (uuid != null) uuid.toString else null

      private def fromString(uuidString: String): UUID = if (uuidString != null) UUID.fromString(uuidString) else null
    }
  }

}
