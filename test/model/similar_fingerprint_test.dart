import 'package:aves/model/duplicates/similar_fingerprint.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('缓存指纹往返后保留全部比较输入', () {
    const fingerprint = SimilarFingerprint(
      entryId: 7,
      uri: 'content://media/7',
      sizeBytes: 123,
      dateModifiedMillis: 456,
      algorithmVersion: similarMediaFingerprintAlgorithmVersion,
      kind: 'video',
      width: 1920,
      height: 1080,
      durationMillis: 9876,
      imagePHash: null,
      imageDHash: null,
      videoPHashes: ['0000000000000001', '0000000000000002'],
      videoDHashes: ['0000000000000003', '0000000000000004'],
    );

    final restored = SimilarFingerprint.fromDatabaseMap(
      SimilarFingerprint.fromMap(fingerprint.toMap()).toDatabaseMap(),
    );

    expect(restored.entryId, fingerprint.entryId);
    expect(restored.uri, fingerprint.uri);
    expect(restored.sizeBytes, fingerprint.sizeBytes);
    expect(restored.dateModifiedMillis, fingerprint.dateModifiedMillis);
    expect(restored.algorithmVersion, fingerprint.algorithmVersion);
    expect(restored.kind, fingerprint.kind);
    expect(restored.width, fingerprint.width);
    expect(restored.height, fingerprint.height);
    expect(restored.durationMillis, fingerprint.durationMillis);
    expect(restored.videoPHashes, fingerprint.videoPHashes);
    expect(restored.videoDHashes, fingerprint.videoDHashes);
  });
}
