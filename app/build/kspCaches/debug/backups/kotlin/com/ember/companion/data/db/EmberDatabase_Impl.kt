package com.ember.`companion`.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class EmberDatabase_Impl : EmberDatabase() {
  private val _mediaDao: Lazy<MediaDao> = lazy {
    MediaDao_Impl(this)
  }

  private val _scenarioDao: Lazy<ScenarioDao> = lazy {
    ScenarioDao_Impl(this)
  }

  private val _bookmarkDao: Lazy<BookmarkDao> = lazy {
    BookmarkDao_Impl(this)
  }

  private val _historyDao: Lazy<HistoryDao> = lazy {
    HistoryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "3b978db5f1292e2fb91fecfe08ad7f6c", "9f69414eeb728d7db5f45fbdbfb1e155") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `media_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `uri` TEXT NOT NULL, `kind` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `tags` TEXT NOT NULL, `collection` TEXT NOT NULL, `notes` TEXT NOT NULL, `originUrl` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_sha256` ON `media_items` (`sha256`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_addedAt` ON `media_items` (`addedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_kind` ON `media_items` (`kind`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `scenarios` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `tags` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_scenarios_updatedAt` ON `scenarios` (`updatedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_scenarios_favorite` ON `scenarios` (`favorite`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitedAt` ON `history` (`visitedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3b978db5f1292e2fb91fecfe08ad7f6c')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `media_items`")
        connection.execSQL("DROP TABLE IF EXISTS `scenarios`")
        connection.execSQL("DROP TABLE IF EXISTS `bookmarks`")
        connection.execSQL("DROP TABLE IF EXISTS `history`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsMediaItems: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsMediaItems.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("uri", TableInfo.Column("uri", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("kind", TableInfo.Column("kind", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("mimeType", TableInfo.Column("mimeType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("sizeBytes", TableInfo.Column("sizeBytes", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("durationMs", TableInfo.Column("durationMs", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("width", TableInfo.Column("width", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("height", TableInfo.Column("height", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("sha256", TableInfo.Column("sha256", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("collection", TableInfo.Column("collection", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("notes", TableInfo.Column("notes", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("originUrl", TableInfo.Column("originUrl", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("favorite", TableInfo.Column("favorite", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsMediaItems.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysMediaItems: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesMediaItems: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesMediaItems.add(TableInfo.Index("index_media_items_sha256", false, listOf("sha256"),
            listOf("ASC")))
        _indicesMediaItems.add(TableInfo.Index("index_media_items_addedAt", false,
            listOf("addedAt"), listOf("ASC")))
        _indicesMediaItems.add(TableInfo.Index("index_media_items_kind", false, listOf("kind"),
            listOf("ASC")))
        val _infoMediaItems: TableInfo = TableInfo("media_items", _columnsMediaItems,
            _foreignKeysMediaItems, _indicesMediaItems)
        val _existingMediaItems: TableInfo = read(connection, "media_items")
        if (!_infoMediaItems.equals(_existingMediaItems)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |media_items(com.ember.companion.data.db.MediaItem).
              | Expected:
              |""".trimMargin() + _infoMediaItems + """
              |
              | Found:
              |""".trimMargin() + _existingMediaItems)
        }
        val _columnsScenarios: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsScenarios.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("body", TableInfo.Column("body", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("favorite", TableInfo.Column("favorite", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("rating", TableInfo.Column("rating", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("source", TableInfo.Column("source", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsScenarios.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysScenarios: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesScenarios: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesScenarios.add(TableInfo.Index("index_scenarios_updatedAt", false,
            listOf("updatedAt"), listOf("ASC")))
        _indicesScenarios.add(TableInfo.Index("index_scenarios_favorite", false, listOf("favorite"),
            listOf("ASC")))
        val _infoScenarios: TableInfo = TableInfo("scenarios", _columnsScenarios,
            _foreignKeysScenarios, _indicesScenarios)
        val _existingScenarios: TableInfo = read(connection, "scenarios")
        if (!_infoScenarios.equals(_existingScenarios)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |scenarios(com.ember.companion.data.db.Scenario).
              | Expected:
              |""".trimMargin() + _infoScenarios + """
              |
              | Found:
              |""".trimMargin() + _existingScenarios)
        }
        val _columnsBookmarks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBookmarks.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("url", TableInfo.Column("url", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("folder", TableInfo.Column("folder", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBookmarks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBookmarks: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesBookmarks.add(TableInfo.Index("index_bookmarks_url", true, listOf("url"),
            listOf("ASC")))
        val _infoBookmarks: TableInfo = TableInfo("bookmarks", _columnsBookmarks,
            _foreignKeysBookmarks, _indicesBookmarks)
        val _existingBookmarks: TableInfo = read(connection, "bookmarks")
        if (!_infoBookmarks.equals(_existingBookmarks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |bookmarks(com.ember.companion.data.db.Bookmark).
              | Expected:
              |""".trimMargin() + _infoBookmarks + """
              |
              | Found:
              |""".trimMargin() + _existingBookmarks)
        }
        val _columnsHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHistory.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("url", TableInfo.Column("url", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("visitedAt", TableInfo.Column("visitedAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesHistory.add(TableInfo.Index("index_history_visitedAt", false, listOf("visitedAt"),
            listOf("ASC")))
        _indicesHistory.add(TableInfo.Index("index_history_url", false, listOf("url"),
            listOf("ASC")))
        val _infoHistory: TableInfo = TableInfo("history", _columnsHistory, _foreignKeysHistory,
            _indicesHistory)
        val _existingHistory: TableInfo = read(connection, "history")
        if (!_infoHistory.equals(_existingHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |history(com.ember.companion.data.db.HistoryEntry).
              | Expected:
              |""".trimMargin() + _infoHistory + """
              |
              | Found:
              |""".trimMargin() + _existingHistory)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "media_items", "scenarios",
        "bookmarks", "history")
  }

  public override fun clearAllTables() {
    super.performClear(false, "media_items", "scenarios", "bookmarks", "history")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(MediaDao::class, MediaDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ScenarioDao::class, ScenarioDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BookmarkDao::class, BookmarkDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HistoryDao::class, HistoryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun mediaDao(): MediaDao = _mediaDao.value

  public override fun scenarioDao(): ScenarioDao = _scenarioDao.value

  public override fun bookmarkDao(): BookmarkDao = _bookmarkDao.value

  public override fun historyDao(): HistoryDao = _historyDao.value
}
