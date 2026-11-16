import 'package:aves/model/dynamic_albums.dart';
import 'package:aves/model/filters/container/dynamic_album.dart';
import 'package:aves/model/filters/covered/stored_album.dart';
import 'package:aves/model/grouping/common.dart';
import 'package:aves/model/grouping/convert.dart';
import 'package:aves/services/common/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/db.dart';

void main() {
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  test('动态相册重复初始化后只处理一次分组变更', () async {
    final db = localMediaDb as FakeAvesDb;
    final oldGroupUri = albumGrouping.buildGroupUri(null, 'old');
    final newGroupUri = albumGrouping.buildGroupUri(null, 'new');
    final oldChildUri = GroupingConversion.filterToUri(StoredAlbumFilter('/storage/emulated/0/Pictures/old', null))!;
    final newChildUri = GroupingConversion.filterToUri(StoredAlbumFilter('/storage/emulated/0/Pictures/new', null))!;
    albumGrouping.addToGroup({oldChildUri}, oldGroupUri);
    albumGrouping.addToGroup({newChildUri}, newGroupUri);
    final oldGroup = albumGrouping.uriToFilter(oldGroupUri)!;
    db.dynamicAlbumRows = {
      DynamicAlbumRow(
        name: 'grouped',
        filter: oldGroup,
      ),
    };

    await dynamicAlbums.init();
    await dynamicAlbums.init();
    db.addDynamicAlbumsCalls = 0;
    db.removeDynamicAlbumsCalls = 0;

    albumGrouping.eventBus.fire(GroupUriChangedEvent(oldGroupUri, newGroupUri));
    for (var i = 0; i < 10 && db.addDynamicAlbumsCalls == 0; i++) {
      await Future<void>.delayed(Duration.zero);
    }

    expect(db.removeDynamicAlbumsCalls, 1, reason: '重试初始化不能累积重复事件监听');
    expect(db.addDynamicAlbumsCalls, 1);
    expect(dynamicAlbums.get('grouped'), isA<DynamicAlbumFilter>());
  });
}
