import 'dart:convert';

import 'package:http/http.dart' as http;

class const AppRelease({
  required final String version,
  required final Uri url,
});

class AppUpdateService {
  static final Uri latestReleaseUri = Uri.parse('https://api.github.com/repos/404NotFoundYou/MediaCraft/releases/latest');

  static Future<AppRelease?> checkForUpdate({
    required String currentVersion,
    http.Client? client,
  }) async {
    final ownsClient = client == null;
    final requestClient = client ?? http.Client();
    try {
      final response = await requestClient.get(
        latestReleaseUri,
        headers: const {'Accept': 'application/vnd.github+json'},
      );
      if (response.statusCode != 200) {
        throw Exception('GitHub release request failed with status=${response.statusCode}');
      }

      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('GitHub release response is not an object');
      }

      final tagName = decoded['tag_name'];
      final htmlUrl = decoded['html_url'];
      if (tagName is! String || htmlUrl is! String) {
        throw const FormatException('GitHub release response is missing tag_name or html_url');
      }

      final version = _normalizeVersion(tagName);
      if (version.isEmpty || compareVersions(version, currentVersion) <= 0) return null;

      return AppRelease(version: version, url: Uri.parse(htmlUrl));
    } finally {
      if (ownsClient) requestClient.close();
    }
  }

  static int compareVersions(String left, String right) {
    final leftParts = _versionParts(left);
    final rightParts = _versionParts(right);
    for (var i = 0; i < 3; i++) {
      final result = leftParts[i].compareTo(rightParts[i]);
      if (result != 0) return result;
    }
    return 0;
  }

  static String _normalizeVersion(String value) {
    return value.trim().replaceFirst(RegExp(r'^[vV]'), '').split('+').first;
  }

  static List<int> _versionParts(String value) {
    final parts = _normalizeVersion(value).split('-').first.split('.');
    return List<int>.generate(3, (index) => int.tryParse(index < parts.length ? parts[index] : '') ?? 0);
  }
}
