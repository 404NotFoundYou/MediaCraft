import 'package:aves/services/app_update_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

void main() {
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
}
