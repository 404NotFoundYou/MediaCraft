import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:aves/model/settings/settings.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/widgets/settings/app_export/items.dart';
import 'package:aves/widgets/settings/settings_action_delegate.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../common.dart';

void main() {
  setUpAll(setUpAllServices);
  setUp(setUpServices);
  tearDownAll(tearDownAllServices);

  Map<AppExportItem, Object> decode(String backup) => SettingsActionDelegate.getImportContent(Uint8List.fromList(utf8.encode(backup)));

  test('random JSON, unsupported versions and invalid known values cannot reset existing preferences', () async {
    await settings.store.setString(SettingKeys.themeBrightnessKey, 'dark');
    await settings.store.setInt(SettingKeys.convertQualityKey, 87);
    expect(() => decode('{"name":"synthetic download"}'), throwsFormatException);
    expect(() => decode('{"version":99,"settings":{"theme_brightness":"light"}}'), throwsFormatException);
    expect(() => decode('{"version":null,"theme_brightness":"light"}'), throwsFormatException);
    await expectLater(settings.import({'name': 'random JSON'}), throwsFormatException);
    await expectLater(
      settings.import({
        SettingKeys.themeBrightnessKey: 'light',
        SettingKeys.pinnedFiltersKey: [false],
      }),
      throwsFormatException,
    );
    await settings.store.reload();
    expect(settings.store.getString(SettingKeys.themeBrightnessKey), 'dark');
    expect(settings.store.getInt(SettingKeys.convertQualityKey), 87);
  });

  test('legacy and versioned backups restore known values and leave internal preferences alone', () async {
    await settings.store.setBool(SettingKeys.hasAcceptedTermsKey, true);
    await settings.store.setInt(SettingKeys.convertQualityKey, 87);
    final legacy = decode(
      jsonEncode({
        SettingKeys.themeBrightnessKey: 'dark',
        SettingKeys.infoMapZoomKey: 9,
        SettingKeys.hasAcceptedTermsKey: false,
        'unrecognised_old_key': 'ignored',
      }),
    );
    await settings.import(legacy[AppExportItem.settings]!);
    await settings.store.reload();
    expect(settings.store.getString(SettingKeys.themeBrightnessKey), 'dark');
    expect(settings.store.getDouble(SettingKeys.infoMapZoomKey), 9.0);
    expect(settings.store.get(SettingKeys.convertQualityKey), isNull);
    expect(settings.store.getBool(SettingKeys.hasAcceptedTermsKey), isTrue);
    expect(settings.store.get('unrecognised_old_key'), isNull);

    final source = MediaStoreSource();
    addTearDown(source.dispose);
    final export = SettingsActionDelegate.getExportContent(source: source, toExport: {AppExportItem.settings});
    final roundTrip = SettingsActionDelegate.getImportContent(export)[AppExportItem.settings] as Map;
    expect(roundTrip[SettingKeys.themeBrightnessKey], 'dark');
    expect(roundTrip.containsKey(SettingKeys.hasAcceptedTermsKey), isFalse);
  });

  test('an unfinished or rejected preference write cannot report success or clear omitted settings', () async {
    await settings.store.setInt(SettingKeys.convertQualityKey, 87);
    final previous = SharedPreferencesStorePlatform.instance;
    final blocked = _BlockedSettingsStore(await previous.getAll());
    SharedPreferencesStorePlatform.instance = blocked;
    addTearDown(() async {
      SharedPreferencesStorePlatform.instance = previous;
      await settings.store.reload();
    });
    var done = false;
    final importing = settings.import({SettingKeys.themeBrightnessKey: 'light'}).whenComplete(() => done = true);
    final failure = expectLater(importing, throwsStateError);
    await blocked.writeStarted.future;
    expect(done, isFalse);
    expect(settings.store.getInt(SettingKeys.convertQualityKey), 87);
    blocked.allowWrite.complete(false);
    await failure;
    await settings.store.reload();
    expect(settings.store.getInt(SettingKeys.convertQualityKey), 87);
  });
}

class _BlockedSettingsStore extends InMemorySharedPreferencesStore {
  final writeStarted = Completer<void>();
  final allowWrite = Completer<bool>();

  new(super.data) : super.withData();

  @override
  Future<bool> setValue(String valueType, String key, Object value) async {
    if (key.endsWith(SettingKeys.themeBrightnessKey)) {
      writeStarted.complete();
      if (!await allowWrite.future) return false;
    }
    return super.setValue(valueType, key, value);
  }
}
