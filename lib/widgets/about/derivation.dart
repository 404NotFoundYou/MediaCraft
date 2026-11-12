import 'package:aves/widgets/about/app_ref.dart';
import 'package:material_ui/material_ui.dart';

class DerivationNotice extends StatelessWidget {
  const new({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: .start,
        children: [
          const Text(
            'MediaCraft 基于 Aves 的完整源码构建，上游基线为 '
            '9d3f1ff19f680db043c9d80831c6f222e0f8220c，'
            '依照 BSD 3-Clause License 使用和修改。'
            '当前界面真实引入 liquid_glass_widgets 1.17.2，'
            '依照 MIT License 使用。',
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: AppReference.buildSourceLinks(),
          ),
        ],
      ),
    );
  }
}
