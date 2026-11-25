import 'dart:async';

import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/duplicates/similar_fingerprint.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/services/common/channel.dart';
import 'package:flutter/services.dart';

final duplicateService = DuplicateService();

class DuplicateService {
  static const _channel = AvesMethodChannel('deckers.thibault/aves/duplicates');

  Completer<String>? _starting;
  String? _jobId;

  Stream<DuplicateScanEvent> scan(Iterable<AvesEntry> entries, {bool onlyNew = false}) => _start(entries, false, onlyNew);

  Stream<DuplicateScanEvent> scanSimilar(Iterable<AvesEntry> entries, {bool onlyNew = false}) => _start(entries, true, onlyNew);

  Stream<DuplicateScanEvent> _start(Iterable<AvesEntry> entries, bool similar, bool onlyNew) async* {
    final starting = Completer<String>();
    if (_starting != null) throw StateError('正在启动扫描');
    _starting = starting;
    // Cancellation can arrive while the database is loading the cached fingerprints.
    starting.future.ignore();
    try {
      final candidates = <DuplicateMember>[];
      final issues = <Map<String, Object?>>[];
      for (final entry in entries) {
        if (entry.sizeBytes == null || entry.sizeBytes! < 0) {
          issues.add({'entryId': entry.id, 'uri': entry.uri, 'code': 'invalid-size', 'message': '媒体大小未知'});
        } else {
          candidates.add(DuplicateMember.fromEntry(entry));
        }
      }
      final cached = similar ? await localMediaDb.loadSimilarFingerprints(candidates.map((v) => v.entryId).toSet()) : const <SimilarFingerprint>{};
      final id = await _channel.invokeMethod<String>(similar ? 'scanSimilar' : 'scan', {
        'members': candidates.map((member) => member.toMap()).toList(),
        'issues': issues,
        'onlyNew': onlyNew,
        if (similar) 'cachedFingerprints': cached.where((fp) => fp.algorithmVersion == similarMediaFingerprintAlgorithmVersion).map((fp) => fp.toMap()).toList(),
      });
      if (id == null) throw StateError('扫描任务未创建');
      _jobId = id;
      starting.complete(id);
    } catch (error, stack) {
      starting.completeError(error, stack);
      rethrow;
    } finally {
      _starting = null;
    }
    yield* watch(_jobId!);
  }

  Future<List<Map>> history() async => (await _channel.invokeListMethod<Map>('scanHistory')) ?? [];

  Stream<DuplicateScanEvent> watch(String id) async* {
    _jobId = id;
    while (true) {
      final data = await _channel.invokeMapMethod('scanStatus', {'id': id});
      if (data == null) throw StateError('读取扫描状态失败');
      if (data.containsKey('groups')) {
        final result = DuplicateScanResult.fromMap(data);
        if (!result.cancelled && result.fingerprints.isNotEmpty) await localMediaDb.saveSimilarFingerprints(result.fingerprints.toSet());
        yield DuplicateScanCompleted(result);
        return;
      }
      yield DuplicateScanProgress(processed: data['processed'] as int, total: data['total'] as int, stage: data['stage'] as String);
      await Future<void>.delayed(const Duration(milliseconds: 500));
    }
  }

  Future<void> cancel() async {
    final id = _starting == null ? _jobId : await _starting!.future;
    if (id != null) await _channel.invokeMethod('cancel', {'id': id});
  }

  Future<Uint8List?> preview(DuplicateMember member, int timeMillis) => _channel.invokeMethod<Uint8List>('samplePreview', {'member': member.toMap(), 'timeMillis': timeMillis});

  Future<DuplicateVerification> verifySelection({
    required DuplicateGroup group,
    required DuplicateMember keeper,
    required Iterable<DuplicateMember> deleteMembers,
  }) async {
    final members = <DuplicateMember>[keeper, ...deleteMembers];
    final distinctEntryIds = members.map((member) => member.entryId).toSet();
    final distinctUris = members.map((member) => member.uri).toSet();
    if (members.length < 2 ||
        distinctEntryIds.length != members.length ||
        distinctUris.length != members.length ||
        members.any(
          (member) => !group.members.any(
            (candidate) => candidate.entryId == member.entryId && candidate.uri == member.uri,
          ),
        )) {
      return const DuplicateVerification(
        valid: false,
        issues: [
          DuplicateIssue(
            entryId: -1,
            uri: '',
            code: 'invalid-selection',
            message: '保留项或待删除项不属于该重复组',
          ),
        ],
      );
    }
    final result = await _channel.invokeMethod(
      'verifySelection',
      <String, Object?>{
        'expectedDigest': group.digest,
        'expectedSizeBytes': group.sizeBytes,
        'members': members.map((member) => member.toMap()).toList(growable: false),
      },
    );
    return DuplicateVerification.fromMap(result as Map);
  }

  Future<DuplicateVerification> verifySimilarSelection({
    required DuplicateGroup group,
    required DuplicateMember keeper,
    required Iterable<DuplicateMember> deleteMembers,
  }) async {
    final members = <DuplicateMember>[keeper, ...deleteMembers];
    final distinctEntryIds = members.map((member) => member.entryId).toSet();
    final distinctUris = members.map((member) => member.uri).toSet();
    if (group.fragment ||
        group.kind == 'exact' ||
        members.length < 2 ||
        distinctEntryIds.length != members.length ||
        distinctUris.length != members.length ||
        members.any(
          (member) => !group.members.any(
            (candidate) => candidate.entryId == member.entryId && candidate.uri == member.uri,
          ),
        )) {
      return const DuplicateVerification(
        valid: false,
        issues: [
          DuplicateIssue(
            entryId: -1,
            uri: '',
            code: 'invalid-selection',
            message: '保留项或待删除项不属于该相似组',
          ),
        ],
      );
    }
    final result = await _channel.invokeMethod(
      'verifySimilarSelection',
      <String, Object?>{
        'expectedKind': group.kind,
        'expectedHighConfidence': group.highConfidence,
        'expectedScore': group.similarityScore,
        'expectedMatchedFrames': group.matchedFrames,
        'expectedTotalFrames': group.totalFrames,
        'members': members.map((member) => member.toMap()).toList(growable: false),
      },
    );
    return DuplicateVerification.fromMap(result as Map);
  }
}
