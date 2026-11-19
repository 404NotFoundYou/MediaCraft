import 'package:aves/image_providers/full_image_provider.dart';
import 'package:aves/image_providers/region_provider.dart';
import 'package:aves/image_providers/thumbnail_provider.dart';
import 'package:collection/collection.dart';
import 'package:flutter/rendering.dart';

class EntryCache {
  static final _requestKeysByUri = <String, Set<Object>>{};

  static void registerKey(Object key) {
    Set<Object> ifAbsent() => {};
    void register(String uri) {
      final keys = _requestKeysByUri.putIfAbsent(uri, ifAbsent);
      keys.removeWhere((existingKey) => existingKey != key && imageCache.statusForKey(existingKey).untracked);
      keys.add(key);
    }

    switch (key) {
      case ThumbnailProviderKey _:
        register(key.uri);
      case FullImage _:
        register(key.uri);
      case RegionProviderKey _:
        register(key.uri);
      default:
        debugPrint('failed to register image cache key because of unknown type for key=$key');
    }
  }

  static void evict(String uri) {
    final keys = _requestKeysByUri.remove(uri) ?? {};
    debugPrint('Evict cached images for uri=$uri: ${keys.length} imageCache keys');
    keys.forEach(imageCache.evict);
  }

  // return keys sorted by descending extent
  static List<ThumbnailProviderKey> getThumbnailProviderKeys(String uri) {
    final keys = _requestKeysByUri[uri];
    keys?.removeWhere((key) => imageCache.statusForKey(key).untracked);
    if (keys?.isEmpty == true) _requestKeysByUri.remove(uri);
    return (keys ?? {}).whereType<ThumbnailProviderKey>().sortedByCompare((key) => key.extent, (a, b) => b.compareTo(a));
  }
}
