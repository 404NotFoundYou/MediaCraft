import 'package:sqflite/sqflite.dart';

class SqfliteLocalMediaDbSchema {
  static const entryTable = 'entry';
  static const dateTakenTable = 'dateTaken';
  static const metadataTable = 'metadata';
  static const addressTable = 'address';
  static const favouriteTable = 'favourites';
  static const coverTable = 'covers';
  static const dynamicAlbumTable = 'dynamicAlbums';
  static const vaultTable = 'vaults';
  static const trashTable = 'trash';
  static const videoPlaybackTable = 'videoPlayback';
  static const similarFingerprintTable = 'similarFingerprints';
  static const debugTable = 'debug';

  static const allTables = [
    entryTable,
    dateTakenTable,
    metadataTable,
    addressTable,
    favouriteTable,
    coverTable,
    dynamicAlbumTable,
    vaultTable,
    trashTable,
    videoPlaybackTable,
    similarFingerprintTable,
    debugTable,
  ];

  static Future<void> createLatestVersion(Database db) async {
    await Future.forEach(allTables, (table) => createTable(db, table));
    await createLiveDuplicatesIndex(db);
    await createSearchIndex(db);
  }

  static Future<void> createLiveDuplicatesIndex(Database db) => db.execute(
    'CREATE INDEX IF NOT EXISTS entry_content_id_origin_trashed ON $entryTable (contentId, origin, trashed)',
  );

  static Future<void> createSearchIndex(Database db) async {
    await db.execute('CREATE INDEX IF NOT EXISTS entry_live_date ON $entryTable (trashed, sourceDateTakenMillis DESC, id)');
    try {
      await db.execute("CREATE VIRTUAL TABLE entrySearch USING fts5(title, path, tokenize='trigram', detail=none)");
    } on DatabaseException catch (error) {
      final message = error.toString().toLowerCase();
      if (message.contains('no such module') || message.contains('no such tokenizer')) return;
      rethrow;
    }
    const upsert = 'INSERT OR REPLACE INTO entrySearch(rowid,title,path) VALUES(new.id,new.title,new.path)';
    await db.execute('CREATE TRIGGER entry_search_insert AFTER INSERT ON $entryTable BEGIN $upsert; END');
    await db.execute('CREATE TRIGGER entry_search_update AFTER UPDATE OF id,title,path ON $entryTable BEGIN DELETE FROM entrySearch WHERE rowid=old.id; $upsert; END');
    await db.execute('CREATE TRIGGER entry_search_delete AFTER DELETE ON $entryTable BEGIN DELETE FROM entrySearch WHERE rowid=old.id; END');
    await db.execute('INSERT INTO entrySearch(rowid,title,path) SELECT id,title,path FROM $entryTable');
  }

  static Future<void> createTable(Database db, String table) {
    switch (table) {
      case entryTable:
        return db.execute(
          'CREATE TABLE $entryTable('
          'id INTEGER PRIMARY KEY'
          ', contentId INTEGER'
          ', uri TEXT'
          ', path TEXT'
          ', sourceMimeType TEXT'
          ', width INTEGER'
          ', height INTEGER'
          ', sourceRotationDegrees INTEGER'
          ', sizeBytes INTEGER'
          ', title TEXT'
          ', dateAddedSecs INTEGER DEFAULT (strftime(\'%s\',\'now\'))'
          ', dateModifiedMillis INTEGER'
          ', sourceDateTakenMillis INTEGER'
          ', durationMillis INTEGER'
          ', trashed INTEGER DEFAULT 0'
          ', origin INTEGER DEFAULT 0'
          ')',
        );
      case dateTakenTable:
        return db.execute(
          'CREATE TABLE $dateTakenTable('
          'id INTEGER PRIMARY KEY'
          ', dateMillis INTEGER NOT NULL'
          ', timeZoneOffsetMinutes INTEGER'
          ')',
        );
      case metadataTable:
        return db.execute(
          'CREATE TABLE $metadataTable('
          'id INTEGER PRIMARY KEY'
          ', mimeType TEXT'
          ', dateMillis INTEGER'
          ', flags INTEGER'
          ', rotationDegrees INTEGER'
          ', xmpSubjects TEXT'
          ', xmpTitle TEXT'
          ', latitude REAL'
          ', longitude REAL'
          ', rating INTEGER'
          ', timeZoneOffsetMinutes INTEGER'
          ')',
        );
      case addressTable:
        return db.execute(
          'CREATE TABLE $addressTable('
          'id INTEGER PRIMARY KEY'
          ', addressLine TEXT'
          ', countryCode TEXT'
          ', countryName TEXT'
          ', adminArea TEXT'
          ', locality TEXT'
          ')',
        );
      case favouriteTable:
        return db.execute(
          'CREATE TABLE $favouriteTable('
          'id INTEGER PRIMARY KEY'
          ')',
        );
      case coverTable:
        return db.execute(
          'CREATE TABLE $coverTable('
          'filter TEXT PRIMARY KEY'
          ', entryId INTEGER'
          ', packageName TEXT'
          ', color TEXT'
          ')',
        );
      case dynamicAlbumTable:
        return db.execute(
          'CREATE TABLE $dynamicAlbumTable('
          'name TEXT PRIMARY KEY'
          ', filter TEXT'
          ')',
        );
      case vaultTable:
        return db.execute(
          'CREATE TABLE $vaultTable('
          'name TEXT PRIMARY KEY'
          ', autoLock INTEGER'
          ', useBin INTEGER'
          ', lockType TEXT'
          ')',
        );
      case trashTable:
        return db.execute(
          'CREATE TABLE $trashTable('
          'id INTEGER PRIMARY KEY'
          ', path TEXT'
          ', dateMillis INTEGER'
          ')',
        );
      case videoPlaybackTable:
        return db.execute(
          'CREATE TABLE $videoPlaybackTable('
          'id INTEGER PRIMARY KEY'
          ', resumeTimeMillis INTEGER'
          ')',
        );
      case similarFingerprintTable:
        return db.execute(
          'CREATE TABLE $similarFingerprintTable('
          'id INTEGER PRIMARY KEY'
          ', uri TEXT NOT NULL'
          ', sizeBytes INTEGER NOT NULL'
          ', dateModifiedMillis INTEGER'
          ', algorithmVersion INTEGER NOT NULL'
          ', kind TEXT NOT NULL'
          ', width INTEGER NOT NULL'
          ', height INTEGER NOT NULL'
          ', durationMillis INTEGER'
          ', rotationDegrees INTEGER NOT NULL'
          ', isFlipped INTEGER NOT NULL'
          ', pageId INTEGER'
          ', mimeType TEXT'
          ', clipPHashes TEXT'
          ', clipDHashes TEXT'
          ', imagePHash TEXT'
          ', imageDHash TEXT'
          ', videoPHashes TEXT'
          ', videoDHashes TEXT'
          ')',
        );
      case debugTable:
        return db.execute(
          'CREATE TABLE $debugTable('
          'id INTEGER PRIMARY KEY AUTOINCREMENT'
          ', message TEXT'
          ')',
        );
      default:
        throw Exception('unknown table=$table');
    }
  }
}
