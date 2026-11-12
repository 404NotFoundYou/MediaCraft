import 'package:aves/model/entry/entry.dart';

class DuplicateMember {
  final int entryId;
  final String uri;
  final int sizeBytes;
  final int? dateModifiedMillis;

  const new({
    required this.entryId,
    required this.uri,
    required this.sizeBytes,
    required this.dateModifiedMillis,
  });

  factory fromEntry(AvesEntry entry) => DuplicateMember(
    entryId: entry.id,
    uri: entry.uri,
    sizeBytes: entry.sizeBytes!,
    dateModifiedMillis: entry.dateModifiedMillis,
  );

  factory fromMap(Map map) => DuplicateMember(
    entryId: map['entryId'] as int,
    uri: map['uri'] as String,
    sizeBytes: map['sizeBytes'] as int,
    dateModifiedMillis: map['dateModifiedMillis'] as int?,
  );

  Map<String, Object?> toMap() => {
    'entryId': entryId,
    'uri': uri,
    'sizeBytes': sizeBytes,
    'dateModifiedMillis': dateModifiedMillis,
  };
}

class DuplicateGroup {
  final String id;
  final int sizeBytes;
  final String digest;
  final List<DuplicateMember> members;

  const new({
    required this.id,
    required this.sizeBytes,
    required this.digest,
    required this.members,
  });

  factory fromMap(Map map) => DuplicateGroup(
    id: map['id'] as String,
    sizeBytes: map['sizeBytes'] as int,
    digest: map['digest'] as String,
    members: (map['members'] as List).map((v) => DuplicateMember.fromMap(v as Map)).toList(growable: false),
  );
}

class DuplicateIssue {
  final int entryId;
  final String uri;
  final String code;
  final String message;

  const new({
    required this.entryId,
    required this.uri,
    required this.code,
    required this.message,
  });

  factory fromMap(Map map) => DuplicateIssue(
    entryId: map['entryId'] as int,
    uri: map['uri'] as String,
    code: map['code'] as String,
    message: map['message'] as String,
  );
}

class DuplicateScanResult {
  final List<DuplicateGroup> groups;
  final List<DuplicateIssue> issues;
  final bool cancelled;

  const new({
    required this.groups,
    required this.issues,
    required this.cancelled,
  });

  factory fromMap(Map map) => DuplicateScanResult(
    groups: (map['groups'] as List).map((v) => DuplicateGroup.fromMap(v as Map)).toList(growable: false),
    issues: (map['issues'] as List).map((v) => DuplicateIssue.fromMap(v as Map)).toList(growable: false),
    cancelled: map['cancelled'] as bool? ?? false,
  );
}

sealed class DuplicateScanEvent {
  const new();
}

class DuplicateScanProgress extends DuplicateScanEvent {
  final int processed;
  final int total;
  final String stage;

  const new({
    required this.processed,
    required this.total,
    required this.stage,
  });
}

class DuplicateScanCompleted extends DuplicateScanEvent {
  final DuplicateScanResult result;

  const new(this.result);
}

class DuplicateVerification {
  final bool valid;
  final List<DuplicateIssue> issues;

  const new({required this.valid, required this.issues});

  factory fromMap(Map map) => DuplicateVerification(
    valid: map['valid'] as bool,
    issues: (map['issues'] as List).map((v) => DuplicateIssue.fromMap(v as Map)).toList(growable: false),
  );
}
