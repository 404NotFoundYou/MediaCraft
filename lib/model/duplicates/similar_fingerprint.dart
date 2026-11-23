const similarMediaFingerprintAlgorithmVersion = 2;

class const SimilarFingerprint({
  required final int entryId,
  required final String uri,
  required final int sizeBytes,
  required final int? dateModifiedMillis,
  required final int algorithmVersion,
  required final String kind,
  required final int width,
  required final int height,
  required final int? durationMillis,
  required final String? imagePHash,
  required final String? imageDHash,
  required final List<String> videoPHashes,
  required final List<String> videoDHashes,
}) {
  factory fromMap(Map map) => .new(
    entryId: map['entryId'] as int,
    uri: map['uri'] as String,
    sizeBytes: map['sizeBytes'] as int,
    dateModifiedMillis: map['dateModifiedMillis'] as int?,
    algorithmVersion: map['algorithmVersion'] as int,
    kind: map['kind'] as String,
    width: map['width'] as int? ?? 0,
    height: map['height'] as int? ?? 0,
    durationMillis: map['durationMillis'] as int?,
    imagePHash: map['imagePHash'] as String?,
    imageDHash: map['imageDHash'] as String?,
    videoPHashes: (map['videoPHashes'] as List? ?? const []).cast<String>().toList(growable: false),
    videoDHashes: (map['videoDHashes'] as List? ?? const []).cast<String>().toList(growable: false),
  );

  Map<String, Object?> toMap() => {
    'entryId': entryId,
    'uri': uri,
    'sizeBytes': sizeBytes,
    'dateModifiedMillis': dateModifiedMillis,
    'algorithmVersion': algorithmVersion,
    'kind': kind,
    'width': width,
    'height': height,
    'durationMillis': durationMillis,
    'imagePHash': imagePHash,
    'imageDHash': imageDHash,
    'videoPHashes': videoPHashes,
    'videoDHashes': videoDHashes,
  };

  Map<String, Object?> toDatabaseMap() => {
    'id': entryId,
    'uri': uri,
    'sizeBytes': sizeBytes,
    'dateModifiedMillis': dateModifiedMillis,
    'algorithmVersion': algorithmVersion,
    'kind': kind,
    'width': width,
    'height': height,
    'durationMillis': durationMillis,
    'imagePHash': imagePHash,
    'imageDHash': imageDHash,
    'videoPHashes': videoPHashes.join(','),
    'videoDHashes': videoDHashes.join(','),
  };

  factory fromDatabaseMap(Map<String, Object?> map) => .new(
    entryId: map['id'] as int,
    uri: map['uri'] as String,
    sizeBytes: map['sizeBytes'] as int,
    dateModifiedMillis: map['dateModifiedMillis'] as int?,
    algorithmVersion: map['algorithmVersion'] as int,
    kind: map['kind'] as String,
    width: map['width'] as int? ?? 0,
    height: map['height'] as int? ?? 0,
    durationMillis: map['durationMillis'] as int?,
    imagePHash: map['imagePHash'] as String?,
    imageDHash: map['imageDHash'] as String?,
    videoPHashes: _splitHashes(map['videoPHashes'] as String?),
    videoDHashes: _splitHashes(map['videoDHashes'] as String?),
  );

  static List<String> _splitHashes(String? value) => value == null || value.isEmpty ? const [] : value.split(',');
}
