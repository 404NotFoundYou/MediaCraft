import 'package:aves/locale/intl.dart';
import 'package:aves/model/naming_pattern.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:test/test.dart';

void main() {
  setUpAll(() async {
    await initializeDateFormatting();
  });

  test('mixed processors', () {
    const entryCount = 42;
    final dateFormatLocale = IntlUtils.rootLocaleName;
    expect(
      NamingPattern.from(
        userPattern: 'pure literal',
        entryCount: entryCount,
        localeBcp47: dateFormatLocale,
      ).processors,
      [
        const LiteralNamingProcessor('pure literal'),
      ],
    );
    expect(
      NamingPattern.from(
        userPattern: 'prefix<date,yyyy-MM-ddTHH:mm:ss>suffix',
        entryCount: entryCount,
        localeBcp47: dateFormatLocale,
      ).processors,
      [
        const LiteralNamingProcessor('prefix'),
        DateNamingProcessor('yyyy-MM-ddTHH:mm:ss', dateFormatLocale),
        const LiteralNamingProcessor('suffix'),
      ],
    );
    expect(
      NamingPattern.from(
        userPattern: '<date,yyyy-MM-ddTHH:mm:ss> <name>',
        entryCount: entryCount,
        localeBcp47: dateFormatLocale,
      ).processors,
      [
        DateNamingProcessor('yyyy-MM-ddTHH:mm:ss', dateFormatLocale),
        const LiteralNamingProcessor(' '),
        const NameNamingProcessor(),
      ],
    );
  });

  test('insertion offset', () {
    const userPattern = '<date,yyyy-MM-ddTHH:mm:ss> infix <name>';
    expect(NamingPattern.getInsertionOffset(userPattern, -1), 0);
    expect(NamingPattern.getInsertionOffset(userPattern, 1234), userPattern.length);
    expect(NamingPattern.getInsertionOffset(userPattern, 4), 26);
    expect(NamingPattern.getInsertionOffset(userPattern, 30), 30);
  });

  test('invalid fields fail explicitly instead of disappearing from the generated name', () {
    final locale = IntlUtils.rootLocaleName;

    expect(NamingPattern.from(userPattern: '<nam>', entryCount: 1, localeBcp47: locale).errors, contains('Unknown naming field: <nam>'));
    expect(NamingPattern.from(userPattern: '<date>', entryCount: 1, localeBcp47: locale).errors, contains('Date field requires a format'));
    expect(NamingPattern.from(userPattern: '<hash,crc32>', entryCount: 1, localeBcp47: locale).errors, contains('Unknown hash algorithm: crc32'));
    expect(NamingPattern.from(userPattern: '<field,unknown>', entryCount: 1, localeBcp47: locale).errors, contains('Unknown metadata field: unknown'));
    expect(NamingPattern.from(userPattern: '<counter,padding=-1>', entryCount: 1, localeBcp47: locale).errors, contains('Counter padding cannot be negative'));
    expect(
      NamingPattern.from(userPattern: '<counter,padding=2147483647>', entryCount: 1, localeBcp47: locale).isValid,
      isFalse,
      reason: '输入错误不能让预览分配巨量字符串',
    );
    expect(NamingPattern.from(userPattern: '<counter,padding=255>', entryCount: 1, localeBcp47: locale).isValid, isTrue);
    expect(NamingPattern.from(userPattern: '<name', entryCount: 1, localeBcp47: locale).errors, contains('Malformed naming field'));
    expect(NamingPattern.from(userPattern: '   ', entryCount: 1, localeBcp47: locale).errors, contains('Naming pattern cannot be empty'));
  });

  test('date formats are validated before the rename action starts', () {
    final pattern = NamingPattern.from(
      userPattern: '<date,EEEEEE>',
      entryCount: 1,
      localeBcp47: IntlUtils.rootLocaleName,
    );

    expect(pattern.isValid, isFalse);
    expect(pattern.errors, contains('Invalid date format: EEEEEE'));
  });

  test('generated names cannot escape the source directory', () {
    expect(NamingPattern.validateGeneratedName('photo 001'), isNull);
    expect(NamingPattern.validateGeneratedName(''), isNotNull);
    expect(NamingPattern.validateGeneratedName('../other/photo'), isNotNull);
    expect(NamingPattern.validateGeneratedName(r'sub\photo'), isNotNull);
    expect(NamingPattern.validateGeneratedName('.hidden'), isNotNull);
    expect(NamingPattern.validateGeneratedName('line\nbreak'), isNotNull);
  });
}
