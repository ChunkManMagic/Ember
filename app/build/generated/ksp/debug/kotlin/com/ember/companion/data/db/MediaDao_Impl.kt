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
public class MediaDao_Impl(
  __db: RoomDatabase,
) : MediaDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMediaItem: EntityInsertAdapter<MediaItem>

  private val __deleteAdapterOfMediaItem: EntityDeleteOrUpdateAdapter<MediaItem>

  private val __updateAdapterOfMediaItem: EntityDeleteOrUpdateAdapter<MediaItem>
  init {
    this.__db = __db
    this.__insertAdapterOfMediaItem = object : EntityInsertAdapter<MediaItem>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `media_items` (`id`,`title`,`uri`,`kind`,`mimeType`,`sizeBytes`,`durationMs`,`width`,`height`,`sha256`,`tags`,`collection`,`notes`,`originUrl`,`favorite`,`addedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MediaItem) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.uri)
        statement.bindText(4, entity.kind)
        statement.bindText(5, entity.mimeType)
        statement.bindLong(6, entity.sizeBytes)
        statement.bindLong(7, entity.durationMs)
        statement.bindLong(8, entity.width.toLong())
        statement.bindLong(9, entity.height.toLong())
        statement.bindText(10, entity.sha256)
        statement.bindText(11, entity.tags)
        statement.bindText(12, entity.collection)
        statement.bindText(13, entity.notes)
        statement.bindText(14, entity.originUrl)
        val _tmp: Int = if (entity.favorite) 1 else 0
        statement.bindLong(15, _tmp.toLong())
        statement.bindLong(16, entity.addedAt)
      }
    }
    this.__deleteAdapterOfMediaItem = object : EntityDeleteOrUpdateAdapter<MediaItem>() {
      protected override fun createQuery(): String = "DELETE FROM `media_items` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MediaItem) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfMediaItem = object : EntityDeleteOrUpdateAdapter<MediaItem>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `media_items` SET `id` = ?,`title` = ?,`uri` = ?,`kind` = ?,`mimeType` = ?,`sizeBytes` = ?,`durationMs` = ?,`width` = ?,`height` = ?,`sha256` = ?,`tags` = ?,`collection` = ?,`notes` = ?,`originUrl` = ?,`favorite` = ?,`addedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MediaItem) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.uri)
        statement.bindText(4, entity.kind)
        statement.bindText(5, entity.mimeType)
        statement.bindLong(6, entity.sizeBytes)
        statement.bindLong(7, entity.durationMs)
        statement.bindLong(8, entity.width.toLong())
        statement.bindLong(9, entity.height.toLong())
        statement.bindText(10, entity.sha256)
        statement.bindText(11, entity.tags)
        statement.bindText(12, entity.collection)
        statement.bindText(13, entity.notes)
        statement.bindText(14, entity.originUrl)
        val _tmp: Int = if (entity.favorite) 1 else 0
        statement.bindLong(15, _tmp.toLong())
        statement.bindLong(16, entity.addedAt)
        statement.bindLong(17, entity.id)
      }
    }
  }

  public override suspend fun insert(item: MediaItem): Long = performSuspending(__db, false, true) {
      _connection ->
    val _result: Long = __insertAdapterOfMediaItem.insertAndReturnId(_connection, item)
    _result
  }

  public override suspend fun delete(item: MediaItem): Unit = performSuspending(__db, false, true) {
      _connection ->
    __deleteAdapterOfMediaItem.handle(_connection, item)
  }

  public override suspend fun update(item: MediaItem): Unit = performSuspending(__db, false, true) {
      _connection ->
    __updateAdapterOfMediaItem.handle(_connection, item)
  }

  public override fun observeAll(): Flow<List<MediaItem>> {
    val _sql: String = "SELECT * FROM media_items ORDER BY addedAt DESC"
    return createFlow(__db, false, arrayOf("media_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "sizeBytes")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfWidth: Int = getColumnIndexOrThrow(_stmt, "width")
        val _columnIndexOfHeight: Int = getColumnIndexOrThrow(_stmt, "height")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCollection: Int = getColumnIndexOrThrow(_stmt, "collection")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfOriginUrl: Int = getColumnIndexOrThrow(_stmt, "originUrl")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<MediaItem> = mutableListOf()
        while (_stmt.step()) {
          val _item: MediaItem
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpMimeType: String
          _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpWidth: Int
          _tmpWidth = _stmt.getLong(_columnIndexOfWidth).toInt()
          val _tmpHeight: Int
          _tmpHeight = _stmt.getLong(_columnIndexOfHeight).toInt()
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCollection: String
          _tmpCollection = _stmt.getText(_columnIndexOfCollection)
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpOriginUrl: String
          _tmpOriginUrl = _stmt.getText(_columnIndexOfOriginUrl)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item =
              MediaItem(_tmpId,_tmpTitle,_tmpUri,_tmpKind,_tmpMimeType,_tmpSizeBytes,_tmpDurationMs,_tmpWidth,_tmpHeight,_tmpSha256,_tmpTags,_tmpCollection,_tmpNotes,_tmpOriginUrl,_tmpFavorite,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeById(id: Long): Flow<MediaItem?> {
    val _sql: String = "SELECT * FROM media_items WHERE id = ?"
    return createFlow(__db, false, arrayOf("media_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "sizeBytes")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfWidth: Int = getColumnIndexOrThrow(_stmt, "width")
        val _columnIndexOfHeight: Int = getColumnIndexOrThrow(_stmt, "height")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCollection: Int = getColumnIndexOrThrow(_stmt, "collection")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfOriginUrl: Int = getColumnIndexOrThrow(_stmt, "originUrl")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MediaItem?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpMimeType: String
          _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpWidth: Int
          _tmpWidth = _stmt.getLong(_columnIndexOfWidth).toInt()
          val _tmpHeight: Int
          _tmpHeight = _stmt.getLong(_columnIndexOfHeight).toInt()
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCollection: String
          _tmpCollection = _stmt.getText(_columnIndexOfCollection)
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpOriginUrl: String
          _tmpOriginUrl = _stmt.getText(_columnIndexOfOriginUrl)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result =
              MediaItem(_tmpId,_tmpTitle,_tmpUri,_tmpKind,_tmpMimeType,_tmpSizeBytes,_tmpDurationMs,_tmpWidth,_tmpHeight,_tmpSha256,_tmpTags,_tmpCollection,_tmpNotes,_tmpOriginUrl,_tmpFavorite,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): MediaItem? {
    val _sql: String = "SELECT * FROM media_items WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "sizeBytes")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfWidth: Int = getColumnIndexOrThrow(_stmt, "width")
        val _columnIndexOfHeight: Int = getColumnIndexOrThrow(_stmt, "height")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCollection: Int = getColumnIndexOrThrow(_stmt, "collection")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfOriginUrl: Int = getColumnIndexOrThrow(_stmt, "originUrl")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MediaItem?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpMimeType: String
          _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpWidth: Int
          _tmpWidth = _stmt.getLong(_columnIndexOfWidth).toInt()
          val _tmpHeight: Int
          _tmpHeight = _stmt.getLong(_columnIndexOfHeight).toInt()
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCollection: String
          _tmpCollection = _stmt.getText(_columnIndexOfCollection)
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpOriginUrl: String
          _tmpOriginUrl = _stmt.getText(_columnIndexOfOriginUrl)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result =
              MediaItem(_tmpId,_tmpTitle,_tmpUri,_tmpKind,_tmpMimeType,_tmpSizeBytes,_tmpDurationMs,_tmpWidth,_tmpHeight,_tmpSha256,_tmpTags,_tmpCollection,_tmpNotes,_tmpOriginUrl,_tmpFavorite,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun findByHash(hash: String): MediaItem? {
    val _sql: String = "SELECT * FROM media_items WHERE sha256 = ? AND sha256 != '' LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, hash)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "sizeBytes")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfWidth: Int = getColumnIndexOrThrow(_stmt, "width")
        val _columnIndexOfHeight: Int = getColumnIndexOrThrow(_stmt, "height")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCollection: Int = getColumnIndexOrThrow(_stmt, "collection")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfOriginUrl: Int = getColumnIndexOrThrow(_stmt, "originUrl")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MediaItem?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpMimeType: String
          _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpWidth: Int
          _tmpWidth = _stmt.getLong(_columnIndexOfWidth).toInt()
          val _tmpHeight: Int
          _tmpHeight = _stmt.getLong(_columnIndexOfHeight).toInt()
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCollection: String
          _tmpCollection = _stmt.getText(_columnIndexOfCollection)
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpOriginUrl: String
          _tmpOriginUrl = _stmt.getText(_columnIndexOfOriginUrl)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result =
              MediaItem(_tmpId,_tmpTitle,_tmpUri,_tmpKind,_tmpMimeType,_tmpSizeBytes,_tmpDurationMs,_tmpWidth,_tmpHeight,_tmpSha256,_tmpTags,_tmpCollection,_tmpNotes,_tmpOriginUrl,_tmpFavorite,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeByKind(kind: String): Flow<List<MediaItem>> {
    val _sql: String = "SELECT * FROM media_items WHERE kind = ? ORDER BY addedAt DESC"
    return createFlow(__db, false, arrayOf("media_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, kind)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfUri: Int = getColumnIndexOrThrow(_stmt, "uri")
        val _columnIndexOfKind: Int = getColumnIndexOrThrow(_stmt, "kind")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfSizeBytes: Int = getColumnIndexOrThrow(_stmt, "sizeBytes")
        val _columnIndexOfDurationMs: Int = getColumnIndexOrThrow(_stmt, "durationMs")
        val _columnIndexOfWidth: Int = getColumnIndexOrThrow(_stmt, "width")
        val _columnIndexOfHeight: Int = getColumnIndexOrThrow(_stmt, "height")
        val _columnIndexOfSha256: Int = getColumnIndexOrThrow(_stmt, "sha256")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfCollection: Int = getColumnIndexOrThrow(_stmt, "collection")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfOriginUrl: Int = getColumnIndexOrThrow(_stmt, "originUrl")
        val _columnIndexOfFavorite: Int = getColumnIndexOrThrow(_stmt, "favorite")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<MediaItem> = mutableListOf()
        while (_stmt.step()) {
          val _item: MediaItem
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpUri: String
          _tmpUri = _stmt.getText(_columnIndexOfUri)
          val _tmpKind: String
          _tmpKind = _stmt.getText(_columnIndexOfKind)
          val _tmpMimeType: String
          _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          val _tmpSizeBytes: Long
          _tmpSizeBytes = _stmt.getLong(_columnIndexOfSizeBytes)
          val _tmpDurationMs: Long
          _tmpDurationMs = _stmt.getLong(_columnIndexOfDurationMs)
          val _tmpWidth: Int
          _tmpWidth = _stmt.getLong(_columnIndexOfWidth).toInt()
          val _tmpHeight: Int
          _tmpHeight = _stmt.getLong(_columnIndexOfHeight).toInt()
          val _tmpSha256: String
          _tmpSha256 = _stmt.getText(_columnIndexOfSha256)
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpCollection: String
          _tmpCollection = _stmt.getText(_columnIndexOfCollection)
          val _tmpNotes: String
          _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          val _tmpOriginUrl: String
          _tmpOriginUrl = _stmt.getText(_columnIndexOfOriginUrl)
          val _tmpFavorite: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfFavorite).toInt()
          _tmpFavorite = _tmp != 0
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item =
              MediaItem(_tmpId,_tmpTitle,_tmpUri,_tmpKind,_tmpMimeType,_tmpSizeBytes,_tmpDurationMs,_tmpWidth,_tmpHeight,_tmpSha256,_tmpTags,_tmpCollection,_tmpNotes,_tmpOriginUrl,_tmpFavorite,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM media_items"
    return createFlow(__db, false, arrayOf("media_items")) { _connection ->
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

  public override fun observeCollections(): Flow<List<String>> {
    val _sql: String =
        "SELECT DISTINCT collection FROM media_items WHERE collection != '' ORDER BY collection ASC"
    return createFlow(__db, false, arrayOf("media_items")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: MutableList<String> = mutableListOf()
        while (_stmt.step()) {
          val _item: String
          _item = _stmt.getText(0)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM media_items"
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
