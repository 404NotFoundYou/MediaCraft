import 'dart:io';

import 'package:aves/model/entry/origins.dart';
import 'package:aves/model/source/media_store_source.dart';
import 'package:aves/model/vaults/details.dart';
import 'package:aves/model/vaults/vaults.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves_model/aves_model.dart';
import 'package:flutter_test/flutter_test.dart';

import '../common.dart';
import '../fake/db.dart';
import '../fake/media_fetch_service.dart';
import '../fake/media_store_service.dart';
import '../fake/storage_service.dart';

void main() {
  setUpAll(setUpAllServices);
  tearDownAll(tearDownAllServices);

  for (final inVault in [false, true]) {
    test('${inVault ? 'vault' : 'trash'} recovery preserves files on probe failure and recovers later items', () async {
      final dir = await Directory.systemTemp.createTemp('mediacraft-recovery-');
      final unreadable = await File(pContext.join(dir.path, 'unreadable.jpg')).writeAsString('keep even if probing throws');
      final unknown = await File(pContext.join(dir.path, 'unknown.jpg')).writeAsString('keep even if detection returns null');
      final nestedDir = await Directory(pContext.join(dir.path, 'nested')).create();
      final nested = await File(pContext.join(nestedDir.path, 'nested.jpg')).writeAsString('keep files below a directory');
      final recognised = await File(pContext.join(dir.path, 'valid.jpg')).writeAsString('recognised');
      addTearDown(() async {
        for (final file in [unreadable, unknown, nested, recognised]) {
          if (await file.exists()) await file.delete();
        }
        if (await nestedDir.exists()) await nestedDir.delete();
        await dir.delete();
      });

      final recognisedUri = Uri.file(recognised.path).toString();
      final storage = storageService as FakeStorageService;
      final paths = {unreadable.path, nestedDir.path, unknown.path, recognised.path};
      final fetch = mediaFetchService as FakeMediaFetchService;
      fetch
        ..unreadableUris = {Uri.file(unreadable.path).toString()}
        ..entries = {FakeMediaStoreService.newImage(dir.path, 'valid').copyWith(uri: recognisedUri, path: recognised.path)};
      final source = MediaStoreSource();
      addTearDown(source.dispose);
      if (inVault) {
        const vault = VaultDetails(name: 'recovery', autoLockScreenOff: false, useBin: false, lockType: VaultLockType.system);
        (localMediaDb as FakeAvesDb).vaultRows = {vault};
        await vaults.init();
        storage.untrackedVaultPaths = paths;
        final recovered = (await vaults.recoverUntrackedItems(source, vault.path)).single;
        expect(recovered.uri, recognisedUri);
        expect(recovered.origin, EntryOrigins.vault);
      } else {
        storage.untrackedTrashPaths = paths;
        final recovered = (await source.recoverUntrackedTrashItems()).single;
        expect(recovered.uri, recognisedUri);
        expect(recovered.trashDetails?.path, recognised.path);
        expect(recovered.trashed, isTrue);
      }
      expect(await unreadable.readAsString(), 'keep even if probing throws');
      expect(await unknown.readAsString(), 'keep even if detection returns null');
      expect(await nested.readAsString(), 'keep files below a directory');
    });
  }
}
