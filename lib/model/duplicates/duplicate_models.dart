import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/props.dart';
import 'package:aves/model/duplicates/similar_fingerprint.dart';

class DuplicateMember {
  final int entryId;
  final String uri;
  final int sizeBytes;
  final int? dateModifiedMillis;
  final String? mimeType;
  final String? path;
  final int? pageId;
  final int width;
  final int height;
  final int? durationMillis;
  final int rotationDegrees;
  final bool isFlipped;
  final bool isVideo;

  const new({
    required this.entryId,
    required this.uri,
    required this.sizeBytes,
    required this.dateModifiedMillis,
    this.mimeType,
    this.path,
    this.pageId,
    this.width = 0,
    this.height = 0,
    this.durationMillis,
    this.rotationDegrees = 0,
    this.isFlipped = false,
    this.isVideo = false,
  });

  factory fromEntry(AvesEntry entry) => DuplicateMember(
    entryId: entry.id,
    uri: entry.uri,
    sizeBytes: entry.sizeBytes!,
    dateModifiedMillis: entry.dateModifiedMillis,
    mimeType: entry.mimeType,
    path: entry.path,
    pageId: entry.pageId,
    width: entry.width,
    height: entry.height,
    durationMillis: entry.durationMillis,
    rotationDegrees: entry.rotationDegrees,
    isFlipped: entry.isFlipped,
    isVideo: entry.isVideo,
  );

  factory fromMap(Map map) => DuplicateMember(
    entryId: map['entryId'] as int,
    uri: map['uri'] as String,
    sizeBytes: map['sizeBytes'] as int,
    dateModifiedMillis: map['dateModifiedMillis'] as int?,
    mimeType: map['mimeType'] as String?,
    path: map['path'] as String?,
    pageId: map['pageId'] as int?,
    width: map['width'] as int? ?? 0,
    height: map['height'] as int? ?? 0,
    durationMillis: map['durationMillis'] as int?,
    rotationDegrees: map['rotationDegrees'] as int? ?? 0,
    isFlipped: map['isFlipped'] as bool? ?? false,
    isVideo: map['isVideo'] as bool? ?? false,
  );

  Map<String, Object?> toMap() => {
    'entryId': entryId,
    'uri': uri,
    'sizeBytes': sizeBytes,
    'dateModifiedMillis': dateModifiedMillis,
    'mimeType': mimeType,
    'path': path,
    'pageId': pageId,
    'width': width,
    'height': height,
    'durationMillis': durationMillis,
    'rotationDegrees': rotationDegrees,
    'isFlipped': isFlipped,
    'isVideo': isVideo,
  };
}

class DuplicateGroup {
  final String id;
  final int sizeBytes;
  final String digest;
  final List<DuplicateMember> members;
  final String kind;
  final bool highConfidence;
  final double? similarityScore;
  final int? matchedFrames;
  final bool fragment;
  final Map<int, List<({int reference, int member, bool matched})>> frames;
  final int? totalFrames;
  final int? durationDeltaMillis;

  const new({
    required this.id,
    required this.sizeBytes,
    required this.digest,
    required this.members,
    this.kind = 'exact',
    this.highConfidence = false,
    this.similarityScore,
    this.matchedFrames,
    this.fragment = false,
    this.frames = const {},
    this.totalFrames,
    this.durationDeltaMillis,
  });

  factory fromMap(Map map) => DuplicateGroup(
    id: map['id'] as String,
    sizeBytes: map['sizeBytes'] as int,
    digest: map['digest'] as String,
    members: (map['members'] as List).map((v) => DuplicateMember.fromMap(v as Map)).toList(growable: false),
    kind: map['kind'] as String? ?? 'exact',
    highConfidence: map['highConfidence'] as bool? ?? false,
    similarityScore: (map['similarityScore'] as num?)?.toDouble(),
    matchedFrames: map['matchedFrames'] as int?,
    fragment: map['fragment'] as bool? ?? false,
    frames: (map['frames'] as Map? ?? {}).map(
      (id, rows) => MapEntry(id as int, [
        for (final row in rows as List) (reference: row[0] as int, member: row[1] as int, matched: row[2] as bool),
      ]),
    ),
    totalFrames: map['totalFrames'] as int?,
    durationDeltaMillis: map['durationDeltaMillis'] as int?,
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
  final List<SimilarFingerprint> fingerprints;

  const new({
    required this.groups,
    required this.issues,
    required this.cancelled,
    this.fingerprints = const [],
  });

  factory fromMap(Map map) => DuplicateScanResult(
    groups: (map['groups'] as List).map((v) => DuplicateGroup.fromMap(v as Map)).toList(growable: false),
    issues: (map['issues'] as List).map((v) => DuplicateIssue.fromMap(v as Map)).toList(growable: false),
    cancelled: map['cancelled'] as bool? ?? false,
    fingerprints: (map['fingerprints'] as List? ?? const []).map((v) => SimilarFingerprint.fromMap(v as Map)).toList(growable: false),
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
