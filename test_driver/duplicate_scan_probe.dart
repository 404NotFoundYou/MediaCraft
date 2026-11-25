// Opt-in Android smoke test. Use a disposable Libre debug installation only.
// External files: moving.mp4 (12 s), audio-only.mp4, long.mp4 (24 s, changing scenes), short.mp4 (6..16 s cut).
// After READY, kill the worker process once checkpoint.db contains at least 32 rows, then relaunch.
import 'dart:async';
import 'dart:io';

import 'package:aves/model/db/db_sqflite.dart';
import 'package:aves/model/db/db_sqflite_schema.dart';
import 'package:aves/model/duplicates/similar_fingerprint.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/utils/android_file_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:sqflite/sqflite.dart';

const _channel = MethodChannel('deckers.thibault/aves/duplicates');
void check(bool condition, String message) {
  if (!condition) throw StateError(message);
}

class _SmokeDb(final String location) extends SqfliteLocalMediaDb {
  @override
  Future<String> get path async => location;
}

Future<void> databaseProbe(Directory dir) async {
  final path = '${dir.path}/metadata.db';
  final old = await openDatabase(
    path,
    version: 19,
    onCreate: (db, _) async {
      for (final table in SqfliteLocalMediaDbSchema.allTables.where((t) => t != 'similarFingerprints')) {
        await SqfliteLocalMediaDbSchema.createTable(db, table);
      }
      await db.execute('CREATE TABLE similarFingerprints(id INTEGER PRIMARY KEY)');
      await db.execute('INSERT INTO similarFingerprints VALUES(1)');
      final batch = db.batch();
      for (var i = 1; i <= 20000; i++) {
        batch.insert('entry', {
          'id': i,
          'contentId': i % 19999,
          'origin': 1,
          'uri': 'content://smoke/$i',
          'path': '/DCIM/相册/$i.jpg',
          'title': i == 117 ? 'a-mid-NAME-b' : 'file$i',
          'sourceMimeType': 'image/jpeg',
          'sourceDateTakenMillis': i,
        });
      }
      batch.insert('favourites', {'id': 117});
      await batch.commit(noResult: true);
    },
  );
  await old.close();
  final media = _SmokeDb(path);
  await media.init();
  final db = await openDatabase(path);
  check((await media.loadAllFavourites()).single.entryId == 117, 'migration lost favourite');
  final ids = {for (var i = 1; i <= 20000; i++) i};
  final entries = await media.loadEntriesById(ids);
  check(entries.length == ids.length, 'batched ids missing');
  check((await media.searchLiveDuplicates(1, entries)).single.id == 20000, 'batched duplicate IDs');
  check((await media.searchLiveDuplicates(1, {})).isEmpty, 'empty filter');
  final watch = Stopwatch()..start();
  for (var i = 0; i < 10; i++) {
    check((await media.searchLiveEntries('id-Nam', limit: 9)).single.id == 117, 'substring');
  }
  debugPrint('SCAN_QA DB 20000 rows 10 queries ${watch.elapsedMilliseconds} ms, SQLite ${await db.rawQuery('SELECT sqlite_version()')}, index ${await db.rawQuery("SELECT name FROM sqlite_master WHERE name='entrySearch'")}');
  await db.execute("UPDATE entry SET title='changed' WHERE id=117");
  check((await media.searchLiveEntries('id-Nam')).isEmpty, 'stale old search terms');
  await db.execute("INSERT OR REPLACE INTO entry(id,title,uri,origin,sourceMimeType) VALUES(117,'new-term','content://smoke/117',1,'image/jpeg')");
  check((await media.searchLiveEntries('changed')).isEmpty && (await media.searchLiveEntries('w-ter')).single.id == 117, 'replacement');
  await db.execute('DELETE FROM entry WHERE id=117');
  check((await media.searchLiveEntries('w-ter')).isEmpty, 'deleted search terms');
  await db.close();
}

Future<Map> waitJob(String id) async {
  for (var i = 0; i < 400; i++) {
    final status = (await _channel.invokeMapMethod('scanStatus', {'id': id}))!;
    if (status.containsKey('groups')) return status;
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
  throw StateError('scan timed out');
}

Future<void> probe() async {
  final dir = await Directory.systemTemp.createTemp('duplicate-qa-');
  final fixture = Directory('/storage/emulated/0/Android/data/com.mediacraft.app.libre.debug/files');
  final marker = File('${fixture.path}/qa-job');
  try {
    initPlatformServices();
    await androidFileUtils.init();
    await localMediaDb.init();
    if (await marker.exists()) {
      final id = (await marker.readAsString()).trim();
      check(((await _channel.invokeListMethod<Map>('scanHistory'))!).any((j) => j['id'] == id), 'lost task');
      final result = await waitJob(id);
      final saved = await localMediaDb.loadSimilarFingerprints({for (var i = 0; i < 80; i++) 100 + i});
      check(!result.containsKey('fingerprints') && (result['issues'] as List).isEmpty && (result['groups'] as List).isEmpty, 'resume lost result');
      check(saved.length == 80 && saved.every((fp) => fp.videoPHashes.length == 7 && fp.clipPHashes.isEmpty), 'resume lost fingerprints');
      debugPrint('SCAN_QA PASS restored worker after restart $id');
      return;
    }
    await databaseProbe(dir);
    final moving = File('${fixture.path}/moving.mp4');
    final audio = File('${fixture.path}/audio-only.mp4');
    Map<String, Object?> member(int id, File file) => {
      'entryId': id,
      'uri': file.uri.toString(),
      'sizeBytes': file.lengthSync(),
      'dateModifiedMillis': file.lastModifiedSync().millisecondsSinceEpoch,
      'mimeType': 'video/mp4',
      'path': file.path,
      'isVideo': true,
      'width': 320,
      'height': 180,
    };
    final first = member(1, moving);
    final copied = await moving.copy('${dir.path}/copy.mp4');
    final second = member(2, copied);
    Future<String> enqueue(String mode, List<Map> members, {bool onlyNew = false}) async => (await _channel.invokeMethod<String>(mode, {'members': members, 'onlyNew': onlyNew}))!;
    final exact = await waitJob(await enqueue('scan', [first, second]));
    final group = (exact['groups'] as List).single as Map;
    final verify = (await _channel.invokeMapMethod('verifySelection', {
      'members': [first, second],
      'expectedSizeBytes': group['sizeBytes'],
      'expectedDigest': group['digest'],
    }))!;
    check(verify['valid'] == true, 'byte verification');
    final wide = [
      first,
      for (var i = 0; i < 905; i++) {...first, 'entryId': 1000 + i, 'uri': 'content://smoke/$i', 'sizeBytes': 1000000 + i},
      second,
    ];
    final filtered = await waitJob(await enqueue('scan', wide, onlyNew: true));
    check((filtered['groups'] as List).isEmpty && (filtered['issues'] as List).isEmpty, 'known group crossed query batches');
    final replaced = member(2, await moving.copy('${dir.path}/replaced.mp4'));
    check(((await waitJob(await enqueue('scan', [first, replaced], onlyNew: true)))['groups'] as List).length == 1, 'reused ID with a new URI was hidden');
    final similar = await waitJob(await enqueue('scanSimilar', [first, second, member(3, audio)]));
    check((similar['groups'] as List).isEmpty, 'exact duplicates presented as visual');
    check(((similar['issues'] as List).single as Map)['entryId'] == 3, 'audio-only missing from failures');
    check(!similar.containsKey('fingerprints'), 'history carried the fingerprint cache');
    final cached = await localMediaDb.loadSimilarFingerprints({1, 2});
    check(cached.length == 2 && cached.every((fp) => fp.videoPHashes.length == 7 && fp.clipPHashes.isEmpty), 'same-duration clip reads or incomplete cache');
    // A v0.6.12-format row is reusable even when reading its URI would fail.
    final missing = {...first, 'entryId': 7, 'uri': '${first['uri']}.missing', 'path': '${first['path']}.missing'};
    await localMediaDb.saveSimilarFingerprints({SimilarFingerprint.fromMap({...cached.first.toMap(), ...missing, 'durationMillis': cached.first.durationMillis})});
    check(((await waitJob(await enqueue('scanSimilar', [missing])))['issues'] as List).isEmpty, 'legacy cache not reused');
    final changed = {...missing, 'dateModifiedMillis': 1};
    check(((await waitJob(await enqueue('scanSimilar', [changed])))['issues'] as List).length == 1, 'stale cache reused');
    final clips = [member(8, File('${fixture.path}/long.mp4')), member(9, File('${fixture.path}/short.mp4'))];
    for (var run = 0; run < 2; run++) {
      final report = await waitJob(await enqueue('scanSimilar', clips));
      final match = (report['groups'] as List).single as Map;
      check(match['fragment'] == true && match['highConfidence'] == false, 'offset clip must be review only');
      final aligned = ((match['frames'] as Map).values.single as List).first as List;
      check(aligned.take(2).toSet().containsAll({0, 6000}), 'wrong clip alignment: $aligned');
      check(!report.containsKey('fingerprints'), 'clip result contains cache');
    }
    final preview = await _channel.invokeMethod<Uint8List>('samplePreview', {'member': first, 'timeMillis': 1000});
    check(preview != null && preview.length > 100, 'preview missing');
    debugPrint('SCAN_QA PASS byte verification, incremental, seven frames, contact sheet, audio-only');
    final members = [for (var i = 0; i < 80; i++) member(100 + i, await moving.copy('${fixture.path}/qa-copy-$i.mp4'))];
    final job = await enqueue('scanSimilar', members);
    await marker.writeAsString(job, flush: true);
    debugPrint('SCAN_QA READY restart $job');
  } finally {
    await dir.delete(recursive: true);
  }
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MaterialApp(home: Scaffold(body: Text('Android scan regression probe'))));
  WidgetsBinding.instance.addPostFrameCallback((_) async {
    try {
      await probe();
    } catch (error, stack) {
      debugPrint('SCAN_QA FAIL $error\n$stack');
    }
  });
}
