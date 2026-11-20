import 'dart:typed_data';

import 'package:aves/services/common/output_buffer.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final expectedLength in <int?>[null, 3, 5, 7]) {
    test('keeps the exact payload and format trailer with reported length $expectedLength', () {
      final sink = OutputBuffer(expectedLength: expectedLength)
        ..add(Uint8List.fromList([1, 2]))
        ..add(Uint8List.fromList([3, 4]))
        ..add(Uint8List.fromList([0xCA]))
        ..close();
      expect(sink.bytes, [1, 2, 3, 4, 0xCA]);
      expect(sink.bytes.length, 5);
    });
  }

  test('checks the announced length and actual data before allocating or growing the image buffer', () {
    expect(() => OutputBuffer(expectedLength: 6, maxLength: 5), throwsStateError);
    expect(() => OutputBuffer(expectedLength: -1, maxLength: 5), throwsStateError);
    final sink = OutputBuffer(expectedLength: 1, maxLength: 3)..add([1, 2]);
    expect(() => sink.add([3, 4]), throwsStateError);
    sink.close();
    expect(sink.bytes, [1, 2]);
  });
}
