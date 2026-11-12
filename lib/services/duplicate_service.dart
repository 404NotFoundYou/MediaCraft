import 'dart:async';

import 'package:aves/model/duplicates/duplicate_models.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/services/common/channel.dart';
import 'package:flutter/services.dart';

final duplicateService = DuplicateService();

class DuplicateService {
  static const _channel = AvesMethodChannel('deckers.thibault/aves/duplicates');

  StreamController<DuplicateScanEvent>? _scanController;

  new() {
    _channel.setMethodCallHandler(_onPlatformCall);
  }

  Stream<DuplicateScanEvent> scan(Iterable<AvesEntry> entries) {
    if (_scanController != null) {
      throw StateError('A duplicate scan is already running');
    }
    final candidates = <DuplicateMember>[];
    final inputIssues = <DuplicateIssue>[];
    for (final entry in entries) {
      final sizeBytes = entry.sizeBytes;
      if (sizeBytes == null || sizeBytes < 0) {
        inputIssues.add(
          DuplicateIssue(
            entryId: entry.id,
            uri: entry.uri,
            code: 'invalid-size',
            message: '媒体大小未知，无法进行完全重复检测',
          ),
        );
      } else {
        candidates.add(DuplicateMember.fromEntry(entry));
      }
    }
    final controller = StreamController<DuplicateScanEvent>();
    _scanController = controller;
    unawaited(_runScan(candidates, inputIssues, controller));
    return controller.stream;
  }

  Future<void> cancel() => _channel.invokeMethod('cancel');

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

  Future<void> _runScan(
    List<DuplicateMember> candidates,
    List<DuplicateIssue> inputIssues,
    StreamController<DuplicateScanEvent> controller,
  ) async {
    try {
      final result = await _channel.invokeMethod('scan', <String, Object?>{
        'members': candidates.map((member) => member.toMap()).toList(growable: false),
      });
      final platformResult = DuplicateScanResult.fromMap(result as Map);
      controller.add(
        DuplicateScanCompleted(
          DuplicateScanResult(
            groups: platformResult.groups,
            issues: [...inputIssues, ...platformResult.issues],
            cancelled: platformResult.cancelled,
          ),
        ),
      );
    } catch (error, stack) {
      controller.addError(error, stack);
    } finally {
      if (identical(_scanController, controller)) _scanController = null;
      await controller.close();
    }
  }

  Future<void> _onPlatformCall(MethodCall call) async {
    if (call.method != 'scanProgress') return;
    final args = call.arguments as Map;
    _scanController?.add(
      DuplicateScanProgress(
        processed: args['processed'] as int,
        total: args['total'] as int,
        stage: args['stage'] as String,
      ),
    );
  }
}
