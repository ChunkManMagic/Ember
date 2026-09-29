package com.ember.`companion`.`data`.db

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
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
public class BookmarkDao_Impl(
  __db: RoomDatabase,
) : BookmarkDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBookmark: EntityInsertAdapter<Bookmark>

  private val __insertAdapterOfBookmark_1: EntityInsertAdapter<Bookmark>

  private val __deleteAdapterOfBookmark: EntityDeleteOrUpdateAdapter<Bookmark>
  init {
    this.__db = __db
    this.__insertAdapterOfBookmark = object : EntityInsertAdapter<Bookmark>() {
      protected override fun createQuery(): String =
          "INSERT OR IGNORE INTO `bookmarks` (`id`,`title`,`url`,`folder`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Bookmark) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.url)
        statement.bindText(4, entity.folder)
        statement.bindLong(5, entity.createdAt)
      }
    }
    this.__insertAdapterOfBookmark_1 = object : EntityInsertAdapter<Bookmark>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `bookmarks` (`id`,`title`,`url`,`folder`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Bookmark) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.url)
        statement.bindText(4, entity.folder)
        statement.bindLong(5, entity.createdAt)
      }
    }
    this.__deleteAdapterOfBookmark = object : EntityDeleteOrUpdateAdapter<Bookmark>() {
      protected override fun createQuery(): String = "DELETE FROM `bookmarks` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Bookmark) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(bookmark: Bookmark): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfBookmark.insertAndReturnId(_connection, bookmark)
    _result
  }

  public override suspend fun upsert(bookmark: Bookmark): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfBookmark_1.insertAndReturnId(_connection, bookmark)
    _result
  }

  public override suspend fun delete(bookmark: Bookmark): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfBookmark.handle(_connection, bookmark)
  }

  public override fun observeAll(): Flow<List<Bookmark>> {
    val _sql: String = "SELECT * FROM bookmarks ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("bookmarks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Bookmark> = mutableListOf()
        while (_stmt.step()) {
          val _item: Bookmark
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpFolder: String
          _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item = Bookmark(_tmpId,_tmpTitle,_tmpUrl,_tmpFolder,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findByUrl(url: String): Bookmark? {
    val _sql: String = "SELECT * FROM bookmarks WHERE url = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfFolder: Int = getColumnIndexOrThrow(_stmt, "folder")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: Bookmark?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpFolder: String
          _tmpFolder = _stmt.getText(_columnIndexOfFolder)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _result = Bookmark(_tmpId,_tmpTitle,_tmpUrl,_tmpFolder,_tmpCreatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeIsBookmarked(url: String): Flow<Boolean> {
    val _sql: String = "SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = ?)"
    return createFlow(__db, false, arrayOf("bookmarks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        val _result: Boolean
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp != 0
        } else {
          _result = false
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteByUrl(url: String) {
    val _sql: String = "DELETE FROM bookmarks WHERE url = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM bookmarks"
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
