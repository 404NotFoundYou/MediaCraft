import 'package:aves/convert/convert.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves_model/aves_model.dart';
import 'package:aves_utils/aves_utils.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart';

@immutable
class NamingPattern {
  final List<NamingProcessor> processors;
  final List<String> errors;

  static final processorPattern = RegExp(r'<(.+?)(,(.+?))?>');
  static const processorOptionSeparator = ',';
  static const optionKeyValueSeparator = '=';

  const new(this.processors, {this.errors = const []});

  bool get isValid => errors.isEmpty;

  factory from({
    required String userPattern,
    required int entryCount,
    required String localeBcp47,
  }) {
    final processors = <NamingProcessor>[];
    final errors = <String>[];

    const defaultCounterStart = 1;
    final defaultCounterPadding = '$entryCount'.length;

    var index = 0;
    final matches = processorPattern.allMatches(userPattern).toList();
    final matchedCharacters = List<bool>.filled(userPattern.length, false);
    matches.forEach((match) {
      for (var i = match.start; i < match.end; i++) {
        matchedCharacters[i] = true;
      }
      final start = match.start;
      final end = match.end;
      if (index < start) {
        processors.add(LiteralNamingProcessor(userPattern.substring(index, start)));
        index = start;
      }
      final processorKey = match.group(1)?.trim();
      final processorOptions = match.group(3);
      switch (processorKey) {
        case CounterNamingProcessor.key:
          int? start, padding;
          if (processorOptions != null) {
            for (final option in processorOptions.split(processorOptionSeparator).map((v) => v.trim())) {
              final separatorIndex = option.indexOf(optionKeyValueSeparator);
              if (separatorIndex <= 0) {
                errors.add('Invalid counter option: $option');
                continue;
              }
              final key = option.substring(0, separatorIndex).trim();
              final value = option.substring(separatorIndex + 1).trim();
              final valueInt = int.tryParse(value);
              if (valueInt == null) {
                errors.add('Counter option "$key" must be an integer');
                continue;
              }
              switch (key) {
                case CounterNamingProcessor.optionStart:
                  start = valueInt;
                case CounterNamingProcessor.optionPadding:
                  if (valueInt < 0) {
                    errors.add('Counter padding cannot be negative');
                  } else if (valueInt > CounterNamingProcessor.maxPadding) {
                    errors.add('Counter padding cannot exceed ${CounterNamingProcessor.maxPadding}');
                  } else {
                    padding = valueInt;
                  }
                default:
                  errors.add('Unknown counter option: $key');
              }
            }
          }
          processors.add(CounterNamingProcessor(start: start ?? defaultCounterStart, padding: padding ?? defaultCounterPadding));
        case DateNamingProcessor.key:
          final format = processorOptions?.trim() ?? '';
          if (format.isEmpty) {
            errors.add('Date field requires a format');
          } else {
            try {
              processors.add(DateNamingProcessor(format, localeBcp47));
            } catch (_) {
              errors.add('Invalid date format: $format');
            }
          }
        case HashNamingProcessor.key:
          final function = processorOptions?.trim() ?? '';
          if (function.isEmpty) {
            errors.add('Hash field requires an algorithm');
          } else {
            final processor = HashNamingProcessor(function);
            if (processor.function == null) {
              errors.add('Unknown hash algorithm: $function');
            } else {
              processors.add(processor);
            }
          }
        case MetadataFieldNamingProcessor.key:
          final fieldName = processorOptions?.trim() ?? '';
          if (fieldName.isEmpty) {
            errors.add('Metadata field requires a name');
          } else {
            final processor = MetadataFieldNamingProcessor(fieldName);
            if (processor.field == null) {
              errors.add('Unknown metadata field: $fieldName');
            } else {
              processors.add(processor);
            }
          }
        case NameNamingProcessor.key:
          if (processorOptions != null) errors.add('Name field does not accept options');
          processors.add(const NameNamingProcessor());
        case WidthNamingProcessor.key:
          if (processorOptions != null) errors.add('Width field does not accept options');
          processors.add(WidthNamingProcessor());
        case HeightNamingProcessor.key:
          if (processorOptions != null) errors.add('Height field does not accept options');
          processors.add(HeightNamingProcessor());
        case TagsNamingProcessor.key:
          processors.add(TagsNamingProcessor(processorOptions?.trim() ?? ''));
        default:
          debugPrint('unsupported naming processor: ${match.group(0)}');
          errors.add('Unknown naming field: ${match.group(0)}');
      }
      index = end;
    });
    if (index < userPattern.length) {
      processors.add(LiteralNamingProcessor(userPattern.substring(index, userPattern.length)));
    }

    for (var i = 0; i < userPattern.length; i++) {
      if (!matchedCharacters[i] && {'<', '>'}.contains(userPattern[i])) {
        errors.add('Malformed naming field');
        break;
      }
    }
    if (userPattern.trim().isEmpty) errors.add('Naming pattern cannot be empty');

    return NamingPattern(List.unmodifiable(processors), errors: List.unmodifiable(errors));
  }

  static int getInsertionOffset(String userPattern, int offset) {
    offset = offset.clamp(0, userPattern.length);
    final matches = processorPattern.allMatches(userPattern);
    for (final match in matches) {
      final start = match.start;
      final end = match.end;
      if (offset <= start) return offset;
      if (offset <= end) return end;
    }
    return offset;
  }

  static String defaultPatternFor(String processorKey) {
    switch (processorKey) {
      case DateNamingProcessor.key:
        return '<$processorKey, yyyyMMdd-HHmmss>';
      case HashNamingProcessor.key:
        return '<$processorKey, md5>';
      case TagsNamingProcessor.key:
        return '<$processorKey, ->';
      case CounterNamingProcessor.key:
      case NameNamingProcessor.key:
      default:
        if (processorKey.startsWith(MetadataFieldNamingProcessor.key)) {
          final field = MetadataFieldNamingProcessor.fieldFromKey(processorKey);
          return '<${MetadataFieldNamingProcessor.key}, $field>';
        }
        return '<$processorKey>';
    }
  }

  Future<String> apply(AvesEntry entry, int index) async {
    if (!isValid) throw FormatException(errors.join('; '));
    final fields = processors.expand((v) => v.getRequiredFields()).toSet();
    final fieldValues = fields.isEmpty ? const <String, Object?>{} : await metadataFetchService.getFields(entry, fields);
    final result = processors.map((v) => v.process(entry, index, fieldValues) ?? '').join().trim();
    final validationError = validateGeneratedName(result);
    if (validationError != null) throw FormatException(validationError);
    return result;
  }

  static String? validateGeneratedName(String name) {
    if (name.trim().isEmpty) return 'Generated name cannot be empty';
    if (name == '.' || name == '..') return 'Generated name cannot be a relative path';
    if (name.startsWith('.')) return 'Generated name cannot create a hidden file';
    if (name.contains('/') || name.contains('\\')) return 'Generated name cannot contain path separators';
    if (name.codeUnits.any((v) => v < 0x20)) return 'Generated name cannot contain control characters';
    return null;
  }
}

@immutable
abstract class NamingProcessor extends Equatable {
  const new();

  @override
  List<Object?> get props => [];

  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues);

  Set<MetadataField> getRequiredFields() => {};
}

@immutable
class LiteralNamingProcessor extends NamingProcessor {
  final String text;

  @override
  List<Object?> get props => [text];

  const new(this.text);

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) => text;
}

@immutable
class DateNamingProcessor extends NamingProcessor {
  static const key = 'date';

  final DateFormat format;

  @override
  List<Object?> get props => [format.pattern];

  // format using date pattern with Gregorian calendar
  new(String pattern, String localeBcp47) : format = DateFormat(pattern, localeBcp47) {
    if (format.format(DateTime(2000, 1, 2, 3, 4, 5)).isEmpty) {
      throw const FormatException('Date format produces an empty value');
    }
  }

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    final date = entry.bestDate;
    return date != null ? format.format(date) : null;
  }
}

@immutable
class TagsNamingProcessor extends NamingProcessor {
  static const key = 'tags';
  static const defaultSeparator = ' ';

  final String separator;

  @override
  List<Object?> get props => [separator];

  new(String separator) : separator = separator.isEmpty ? defaultSeparator : separator;

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    return entry.tags.join(separator);
  }
}

@immutable
class MetadataFieldNamingProcessor extends NamingProcessor {
  static const key = 'field';

  static String keyWithField(MetadataField field) => '$key-${field.name}';

  // loose, for user to see and later parse
  static String fieldFromKey(String keyWithField) => keyWithField.substring(key.length + 1);

  late final MetadataField? field;

  @override
  List<Object?> get props => [field];

  new(String field) {
    this.field = MetadataField.values.safeByName(field, ignoreCase: true);
  }

  @override
  Set<MetadataField> getRequiredFields() => {field}.nonNulls.toSet();

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    return fieldValues[field?.toPlatform]?.toString();
  }
}

@immutable
class NameNamingProcessor extends NamingProcessor {
  static const key = 'name';

  const new();

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) => entry.fileNameWithoutExtension;
}

@immutable
class WidthNamingProcessor extends NamingProcessor {
  static const key = 'width';

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    return '${entry.displaySize.width.toInt()}';
  }
}

@immutable
class HeightNamingProcessor extends NamingProcessor {
  static const key = 'height';

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    return '${entry.displaySize.height.toInt()}';
  }
}

@immutable
class CounterNamingProcessor extends NamingProcessor {
  final int start;
  final int padding;

  static const key = 'counter';
  static const optionStart = 'start';
  static const optionPadding = 'padding';
  static const maxPadding = 255;

  @override
  List<Object?> get props => [start, padding];

  const new({
    required this.start,
    required this.padding,
  });

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) => '${index + start}'.padLeft(padding, '0');
}

@immutable
class HashNamingProcessor extends NamingProcessor {
  static const key = 'hash';
  static const optionFunction = 'function';

  late final MetadataField? function;

  @override
  List<Object?> get props => [function];

  new(String function) {
    this.function = MetadataField.values.safeByName('hash$function', ignoreCase: true);
  }

  @override
  Set<MetadataField> getRequiredFields() => {function}.nonNulls.toSet();

  @override
  String? process(AvesEntry entry, int index, Map<String, Object?> fieldValues) {
    return fieldValues[function?.toPlatform]?.toString();
  }
}
