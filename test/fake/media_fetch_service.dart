import 'package:aves/model/entry/entry.dart';
import 'package:aves/services/media/media_fetch_service.dart';
import 'package:collection/collection.dart';
import 'package:test/fake.dart';

class FakeMediaFetchService extends Fake implements MediaFetchService {
  Set<AvesEntry> entries = {};
  Set<String> unreadableUris = {};

  @override
  Future<AvesEntry?> getEntry(String uri, String? mimeType, {bool allowUnsized = false}) async {
    if (unreadableUris.contains(uri)) throw StateError('Could not probe $uri');
    return entries.firstWhereOrNull((v) => v.uri == uri);
  }
}
