import 'package:aves/services/common/services.dart';
import 'package:aves/theme/icons.dart';
import 'package:aves/widgets/common/basic/font_size_icon_theme.dart';
import 'package:aves/widgets/common/basic/scaffold.dart';
import 'package:aves/widgets/common/extensions/build_context.dart';
import 'package:aves/widgets/common/identity/empty.dart';
import 'package:material_ui/material_ui.dart';

class const StorageAccessPage({super.key}) extends StatefulWidget {
  static const routeName = '/settings/storage_access';

  @override
  State<StorageAccessPage> createState() => _StorageAccessPageState();
}

class _StorageAccessPageState extends State<StorageAccessPage> {
  late Future<List<String>> _pathLoader;
  List<String>? _lastPaths;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() => _pathLoader = storagePermissionService.getSafGrantedDirectories();

  @override
  Widget build(BuildContext context) {
    return AvesScaffold(
      appBar: AppBar(
        title: Text(context.l10n.settingsStorageAccessPageTitle),
      ),
      body: SafeArea(
        child: FutureBuilder<List<String>>(
          future: _pathLoader,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return Column(
                children: [
                  const _Header(),
                  Expanded(
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.error_outline, size: 48),
                            const SizedBox(height: 12),
                            Text(snapshot.error.toString(), textAlign: TextAlign.center),
                            const SizedBox(height: 16),
                            FilledButton.icon(
                              onPressed: () {
                                _load();
                                setState(() {});
                              },
                              icon: const Icon(Icons.refresh),
                              label: const Text('重新加载'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              );
            }
            if (snapshot.connectionState != ConnectionState.done && _lastPaths == null) {
              return const Column(
                children: [
                  _Header(),
                  Expanded(child: Center(child: CircularProgressIndicator())),
                ],
              );
            }
            final paths = snapshot.data;
            if (paths != null) {
              _lastPaths = paths..sort();
            }
            if (_lastPaths!.isEmpty) {
              return Column(
                crossAxisAlignment: .start,
                children: [
                  const _Header(),
                  const Divider(),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.all(8),
                      child: EmptyContent(
                        text: context.l10n.settingsStorageAccessEmpty,
                      ),
                    ),
                  ),
                ],
              );
            }

            return ListView(
              padding: const EdgeInsets.only(bottom: 16),
              children: [
                const _Header(),
                ..._lastPaths!.map(
                  (path) => Card(
                    margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                    child: ListTile(
                      leading: const Icon(Icons.folder_outlined),
                      title: Text(path, maxLines: 2, overflow: TextOverflow.ellipsis),
                      subtitle: Text(context.l10n.settingsStorageAccessTile),
                      trailing: IconButton(
                        icon: const Icon(AIcons.clear),
                        onPressed: () async {
                          await storagePermissionService.revokeSafDirectoryAccess(path);
                          _load();
                          setState(() {});
                        },
                        tooltip: context.l10n.settingsStorageAccessRevokeTooltip,
                      ),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class const _Header() extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const FontSizeIconTheme(child: Icon(AIcons.info)),
            const SizedBox(width: 16),
            Expanded(child: Text(context.l10n.settingsStorageAccessBanner)),
          ],
        ),
      ),
    );
  }
}
