import 'dart:convert';
import 'dart:typed_data';

class OutputBuffer extends ByteConversionSinkBase {
  final int? _maxLength;
  final BytesBuilder _chunks = BytesBuilder(copy: false);
  Uint8List? _buffer, _bytes;
  int _length = 0;

  new({int? expectedLength, this._maxLength}) {
    if (expectedLength != null) {
      _checkLength(expectedLength);
      _buffer = Uint8List(expectedLength);
    }
  }

  void _checkLength(int length) {
    if (length < 0 || (_maxLength != null && length > _maxLength)) {
      throw StateError('Byte stream length $length exceeds the allowed range 0..$_maxLength');
    }
  }

  @override
  void add(List<int> chunk) {
    assert(_bytes == null);
    final length = _length + chunk.length;
    _checkLength(length);
    final buffer = _buffer;
    if (buffer != null && length <= buffer.length) {
      buffer.setRange(_length, length, chunk);
    } else {
      if (buffer != null) {
        // The provider's length changed while reading. Keep the bytes already received.
        _chunks.add(Uint8List.sublistView(buffer, 0, _length));
        _buffer = null;
      }
      _chunks.add(chunk);
    }
    _length = length;
  }

  @override
  void close() {
    if (_bytes != null) return;
    final buffer = _buffer;
    _bytes = buffer == null ? _chunks.takeBytes() : Uint8List.sublistView(buffer, 0, _length);
    _buffer = null;
  }

  Uint8List get bytes => _bytes!;
}
