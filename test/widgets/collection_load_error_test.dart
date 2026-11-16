import 'package:aves/l10n/l10n.dart';
import 'package:aves/model/source/analysis_controller.dart';
import 'package:aves/model/source/collection_source.dart';
import 'package:aves/widgets/collection/load_error.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('加载失败时给出明确提示和重试入口', (tester) async {
    final source = _RetrySource();
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        locale: const Locale('zh'),
        home: Scaffold(body: CollectionLoadError(source: source)),
      ),
    );
    expect(find.text('媒体加载失败，请重试。'), findsOneWidget);
    await tester.tap(find.text('重试'));
    await tester.pump();
    expect(source.retries, 1);
    expect(source.retriedScope, CollectionSource.fullScope);
  });
}

class _RetrySource extends Fake implements CollectionSource {
  int retries = 0;
  SourceScope retriedScope;

  @override
  SourceScope get targetScope => CollectionSource.fullScope;

  @override
  Future<void> init({required SourceScope scope, AnalysisController? analysisController, bool loadTopEntriesFirst = false}) async {
    retries++;
    retriedScope = scope;
  }
}
