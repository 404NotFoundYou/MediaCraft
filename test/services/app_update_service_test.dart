import 'dart:async';

import 'package:aves/services/app_update_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

void main() {
  test('超时不是已是最新版，并且下一次请求仍能检查更新', () async {
    final stalledResponse = Completer<http.Response>();
    final releaseResponse = http.Response('{"tag_name":"v0.6.7","html_url":"https://example.com/releases/v0.6.7"}', 200);
    var requests = 0;
    final client = MockClient((_) async => ++requests == 1 ? await stalledResponse.future : releaseResponse);
    addTearDown(client.close);

    await expectLater(
      AppUpdateService.checkForUpdate(currentVersion: '0.6.6', client: client, timeout: const Duration(milliseconds: 10)),
      throwsA(isA<TimeoutException>()),
    );
    final retry = await AppUpdateService.checkForUpdate(currentVersion: '0.6.6', client: client);
    expect(retry?.version, '0.6.7');
    stalledResponse.complete(releaseResponse);
    expect(requests, 2);
  });

  test('only reports a release when its semantic version is newer', () async {
    expect(AppUpdateService.compareVersions('v0.6.1', '0.6.0+184'), greaterThan(0));
    expect(AppUpdateService.compareVersions('0.6.0', 'v0.6.0'), 0);
    expect(AppUpdateService.compareVersions('0.5.9', '0.6.0'), lessThan(0));

    final client = MockClient((request) async {
      expect(request.url, AppUpdateService.latestReleaseUri);
      return http.Response(
        '{"tag_name":"v0.6.1","html_url":"https://github.com/404NotFoundYou/MediaCraft/releases/tag/v0.6.1"}',
        200,
      );
    });

    final update = await AppUpdateService.checkForUpdate(
      currentVersion: '0.6.0',
      client: client,
    );
    expect(update?.version, '0.6.1');
    expect(update?.url.toString(), contains('/releases/tag/v0.6.1'));
  });

  test('does not report the current release as an update', () async {
    final client = MockClient(
      (_) async => http.Response(
        '{"tag_name":"v0.6.0","html_url":"https://github.com/404NotFoundYou/MediaCraft/releases/tag/v0.6.0"}',
        200,
      ),
    );

    final update = await AppUpdateService.checkForUpdate(
      currentVersion: '0.6.0+184',
      client: client,
    );
    expect(update, isNull);
  });

  test('Baidu Latest releases reach older installs without offering the same version again', () async {
    final client = MockClient(
      (_) async => http.Response(
        '{"tag_name":"v0.6.3-baidu","html_url":"https://github.com/404NotFoundYou/MediaCraft/releases/tag/v0.6.3-baidu"}',
        200,
      ),
    );
    addTearDown(client.close);

    final update = await AppUpdateService.checkForUpdate(
      currentVersion: '0.6.2+186',
      client: client,
    );
    expect(update?.version, '0.6.3-baidu');
    expect(update?.url.toString(), 'https://github.com/404NotFoundYou/MediaCraft/releases/tag/v0.6.3-baidu');

    final current = await AppUpdateService.checkForUpdate(
      currentVersion: '0.6.3+187',
      client: client,
    );
    expect(current, isNull);
  });
}
