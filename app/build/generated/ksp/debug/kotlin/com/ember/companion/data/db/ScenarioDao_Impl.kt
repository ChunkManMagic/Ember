package com.ember.`companion`.`data`.db

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.EntityUpsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ScenarioDao_Impl(
  __db: RoomDatabase,
) : ScenarioDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfScenario: EntityInsertAdapter<Scenario>

  private val __deleteAdapterOfScenario: EntityDeleteOrUpdateAdapter<Scenario>

  private val __upsertAdapterOfScenario: EntityUpsertAdapter<Scenario>
  init {
    this.__db = __db
    this.__insertAdapterOfScenario = object : EntityInsertAdapter<Scenario>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `scenarios` (`id`,`title`,`body`,`tags`,`favorite`,`rating`,`source`,`createdAt`,`updatedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Scenario) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.body)
        statement.bindText(4, entity.tags)
        val _tmp: Int = if (entity.favorite) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.rating.toLong())
        statement.bindText(7, entity.source)
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
      }
    }
    this.__deleteAdapterOfScenario = object : EntityDeleteOrUpdateAdapter<Scenario>() {
      protected override fun createQuery(): String = "DELETE FROM `scenarios` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Scenario) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__upsertAdapterOfScenario = EntityUpsertAdapter<Scenario>(object :
        EntityInsertAdapter<Scenario>() {
      protected override fun createQuery(): String =
          "INSERT INTO `scenarios` (`id`,`title`,`body`,`tags`,`favorite`,`rating`,`source`,`createdAt`,`updatedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Scenario) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.body)
        statement.bindText(4, entity.tags)
        val _tmp: Int = if (entity.favorite) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.rating.toLong())
        statement.bindText(7, entity.source)
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
      }
    }, object : EntityDeleteOrUpdateAdapter<Scenario>() {
      protected override fun createQuery(): String =
          "UPDATE `scenarios` SET `id` = ?,`title` = ?,`body` = ?,`tags` = ?,`favorite` = ?,`rating` = ?,`source` = ?,`createdAt` = ?,`updatedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Scenario) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.body)
        statement.bindText(4, entity.tags)
        val _tmp: Int = if (entity.favorite) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.rating.toLong())
        statement.bindText(7, entity.source)
        statement.bindLong(8, entity.createdAt)
        statement.bindLong(9, entity.updatedAt)
        statement.bindLong(10, entity.id)
      }
    })
  }

  public override suspend fun insert(scenario: Scenario): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfScenario.insertAndReturnId(_connection, scenario)
    _result
  }

  public override suspend fun delete(scenario: Scenario): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfScenario.handle(_connection, scenario)
  }

  public override suspend fun upsert(scenario: Scenario): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __upsertAdapterOfScenario.upsertAndReturnId(_connection, scenario)
    _result
  }

  public override fun observeAll(): Flow<List<Scenario>> {
    val _sql: String = "SELECT * FROM scenarios ORDER BY favorite DESC, updatedAt DESC"
    return createFlow(__db, false, arrayOf("scenarios")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<Scenario> = mutableListOf()
        while (_stmt.step()) {
          val _item: Scenario
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpRating: Int
          _tmpRating = _stmt.getLong(_columnIndexOfRating).toInt()
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              Scenario(_tmpId,_tmpTitle,_tmpBody,_tmpTags,_tmpFavorite,_tmpRating,_tmpSource,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeById(id: Long): Flow<Scenario?> {
    val _sql: String = "SELECT * FROM scenarios WHERE id = ?"
    return createFlow(__db, false, arrayOf("scenarios")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: Scenario?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpRating: Int
          _tmpRating = _stmt.getLong(_columnIndexOfRating).toInt()
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              Scenario(_tmpId,_tmpTitle,_tmpBody,_tmpTags,_tmpFavorite,_tmpRating,_tmpSource,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): Scenario? {
    val _sql: String = "SELECT * FROM scenarios WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfBody: Int = getColumnIndexOrThrow(_stmt, "body")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: Scenario?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpBody: String
          _tmpBody = _stmt.getText(_columnIndexOfBody)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpRating: Int
          _tmpRating = _stmt.getLong(_columnIndexOfRating).toInt()
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              Scenario(_tmpId,_tmpTitle,_tmpBody,_tmpTags,_tmpFavorite,_tmpRating,_tmpSource,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM scenarios"
    return createFlow(__db, false, arrayOf("scenarios")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM scenarios"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
