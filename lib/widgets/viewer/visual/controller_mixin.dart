import 'dart:async';
import 'dart:math';

import 'package:aves/app_mode.dart';
import 'package:aves/model/device.dart';
import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/multipage.dart';
import 'package:aves/model/entry/extensions/props.dart';
import 'package:aves/model/settings/settings.dart';
import 'package:aves/services/common/services.dart';
import 'package:aves/theme/durations.dart';
import 'package:aves/widgets/viewer/multipage/conductor.dart';
import 'package:aves/widgets/viewer/multipage/controller.dart';
import 'package:aves/widgets/viewer/video/conductor.dart';
import 'package:aves_model/aves_model.dart';
import 'package:aves_video/aves_video.dart';
import 'package:collection/collection.dart';
import 'package:floating/floating.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:provider/provider.dart';

// state controllers/monitors
mixin EntryViewControllerMixin<T extends StatefulWidget> on State<T> {
  final Map<AvesEntry, VoidCallback> _metadataChangeListeners = {};
  final Map<MultiPageController, Future<void> Function()> _multiPageControllerPageListeners = {};
  int _entryControllerGeneration = 0;

  bool? videoMutedOverride;

  bool get isViewingImage;

  ValueNotifier<AvesEntry?> get entryNotifier;

  Future<void> initEntryControllers(AvesEntry? entry) async {
    if (!mounted || entry == null) return;
    final generation = ++_entryControllerGeneration;

    if (entry.isVideo) {
      await _initVideoController(entry, generation);
      if (!_isCurrentEntryController(entry, generation)) return;
    }
    if (entry.isMultiPage) {
      await _initMultiPageController(entry, generation);
      if (!_isCurrentEntryController(entry, generation)) return;
    }
    void listener() => _onMetadataChanged(entry);
    _metadataChangeListeners[entry] = listener;
    entry.metadataChangeNotifier.addListener(listener);
  }

  void cleanEntryControllers(AvesEntry? entry) {
    _entryControllerGeneration++;
    if (entry == null) return;

    final listener = _metadataChangeListeners.remove(entry);
    if (listener != null) {
      entry.metadataChangeNotifier.removeListener(listener);
    }
    if (entry.isMultiPage) {
      _cleanMultiPageController(entry);
    }
  }

  void _onMetadataChanged(AvesEntry entry) {
    debugPrint('reinitialize controllers for entry=$entry because metadata changed');
    cleanEntryControllers(entry);
    initEntryControllers(entry);
  }

  SlideshowVideoPlayback? get videoPlaybackOverride {
    if (!mounted) return null;
    final appMode = context.read<ValueNotifier<AppMode>>().value;
    switch (appMode) {
      case .screenSaver:
        return settings.screenSaverVideoPlayback;
      case .slideshow:
        return settings.slideshowVideoPlayback;
      default:
        return null;
    }
  }

  bool get videoAutoPlayEnabled {
    if (!isViewingImage) return false;

    switch (videoPlaybackOverride) {
      case .skip:
        return false;
      case .playMuted:
      case .playWithSound:
        return true;
      case null:
        break;
    }

    switch (settings.videoAutoPlayMode) {
      case .disabled:
        return false;
      case .playMuted:
      case .playWithSound:
        return true;
    }
  }

  bool get shouldAutoPlayVideoMuted {
    if (videoMutedOverride != null) {
      return videoMutedOverride!;
    }

    switch (videoPlaybackOverride) {
      case .skip:
      case .playWithSound:
        return false;
      case .playMuted:
        return true;
      case null:
        break;
    }

    switch (settings.videoAutoPlayMode) {
      case .disabled:
      case .playWithSound:
        return false;
      case .playMuted:
        return true;
    }
  }

  bool get shouldAutoPlayMotionPhoto {
    if (!isViewingImage) return false;

    return settings.enableMotionPhotoAutoPlay;
  }

  bool _isCurrentEntryController(AvesEntry entry, int generation) => mounted && generation == _entryControllerGeneration && entry == entryNotifier.value;

  Future<void> _initVideoController(AvesEntry entry, int generation) async {
    final videoConductor = context.read<VideoConductor>();
    final AvesVideoController controller;
    try {
      controller = await videoConductor.getOrCreateController(entry);
    } catch (_) {
      if (!_isCurrentEntryController(entry, generation) || videoConductor.isDisposed) return;
      rethrow;
    }
    if (!_isCurrentEntryController(entry, generation)) return;
    setState(() {});

    if (videoAutoPlayEnabled || entry.isAnimated) {
      final resumeTimeMillis = await controller.getResumeTime(context);
      if (!_isCurrentEntryController(entry, generation)) return;
      await _autoPlayVideo(controller, () => _isCurrentEntryController(entry, generation), resumeTimeMillis: resumeTimeMillis);
    }
  }

  Future<void> _initMultiPageController(AvesEntry entry, int generation) async {
    final multiPageConductor = context.read<MultiPageConductor>();
    final videoConductor = context.read<VideoConductor>();
    final multiPageController = multiPageConductor.getOrCreateController(entry);
    if (!_isCurrentEntryController(entry, generation)) return;
    setState(() {});

    final multiPageInfo = multiPageController.info ?? await multiPageController.infoStream.first;
    if (!_isCurrentEntryController(entry, generation)) return;
    assert(multiPageInfo != null);
    if (multiPageInfo == null) return;

    if (entry.isMotionPhoto) {
      await multiPageInfo.extractMotionPhotoVideo();
      if (!_isCurrentEntryController(entry, generation)) return;
    }

    final videoPageEntries = multiPageInfo.videoPageEntries;
    if (videoPageEntries.isNotEmpty) {
      // init video controllers for all pages that could need it
      await Future.forEach(videoPageEntries, (pageEntry) async {
        if (!_isCurrentEntryController(entry, generation)) return;
        try {
          await videoConductor.getOrCreateController(pageEntry, maxControllerCount: videoPageEntries.length);
        } catch (_) {
          if (!_isCurrentEntryController(entry, generation) || videoConductor.isDisposed) return;
          rethrow;
        }
      });
      if (!_isCurrentEntryController(entry, generation)) return;

      // auto play/pause when changing page
      Future<void> _onPageChanged() async {
        if (!_isCurrentEntryController(entry, generation)) return;
        await videoConductor.pauseAll();
        if (!_isCurrentEntryController(entry, generation)) return;
        if (videoAutoPlayEnabled || (entry.isMotionPhoto && shouldAutoPlayMotionPhoto)) {
          final page = multiPageController.page;
          final pageInfo = multiPageInfo.getByIndex(page)!;
          if (pageInfo.isVideo) {
            final pageEntry = multiPageInfo.getPageEntryByIndex(page);
            final pageVideoController = videoConductor.getController(pageEntry);
            assert(pageVideoController != null);
            if (pageVideoController != null) {
              await _autoPlayVideo(
                pageVideoController,
                () => _isCurrentEntryController(entry, generation) && page == multiPageController.page,
              );
            }
          }
        }
      }

      _multiPageControllerPageListeners[multiPageController] = _onPageChanged;
      multiPageController.pageNotifier.addListener(_onPageChanged);
      await _onPageChanged();
      if (!_isCurrentEntryController(entry, generation)) return;

      if (entry.isMotionPhoto && shouldAutoPlayMotionPhoto) {
        await Future.pause(ADurations.motionPhotoAutoPlayDelay);
        if (_isCurrentEntryController(entry, generation)) {
          multiPageController.page = 1;
        }
      }
    }
  }

  Future<void> _cleanMultiPageController(AvesEntry entry) async {
    final multiPageController = _multiPageControllerPageListeners.keys.firstWhereOrNull((v) => v.entry == entry);
    if (multiPageController != null) {
      final _onPageChange = _multiPageControllerPageListeners.remove(multiPageController);
      if (_onPageChange != null) {
        multiPageController.pageNotifier.removeListener(_onPageChange);
      }
    }
  }

  Future<void> _autoPlayVideo(AvesVideoController videoController, bool Function() isCurrent, {int? resumeTimeMillis}) async {
    // video decoding may fail or have initial artifacts when the player initializes
    // during this widget initialization (because of the page transition and hero animation?)
    // so we play after a delay for increased stability
    await Future.pause(const Duration(milliseconds: 300) * timeDilation);
    if (!isCurrent()) return;

    if (!videoController.isMuted && (videoController.entry.isAnimated || shouldAutoPlayVideoMuted)) {
      await videoController.mute(true);
      if (!isCurrent()) return;
    }

    if (resumeTimeMillis != null) {
      await videoController.seekTo(resumeTimeMillis);
      if (!isCurrent()) return;
    }
    await videoController.play();

    // playing controllers are paused when the entry changes,
    // but the controller may still be preparing (not yet playing) when this happens
    // so we make sure the current entry is still the same to keep playing
    if (!isCurrent()) {
      await videoController.pause();
    }
  }

  Future<void> pauseVideoControllers() => context.read<VideoConductor>().pauseAll();

  static const _pipRatioMax = Rational(43, 18);
  static const _pipRatioMin = Rational(18, 43);

  Future<void> updatePictureInPicture(BuildContext context) async {
    if (!device.supportPictureInPicture) return;

    if (context.mounted && settings.videoBackgroundMode == VideoBackgroundMode.pip) {
      final appMode = context.read<ValueNotifier<AppMode>>().value;
      if (appMode != .screenSaver) {
        final playingController = context.read<VideoConductor>().getPlayingController();
        if (playingController != null) {
          final entrySize = playingController.entry.displaySize;
          final entryAspectRatio = entrySize.aspectRatio;
          final Rational pipAspectRatio;
          if (entryAspectRatio > _pipRatioMax.aspectRatio) {
            pipAspectRatio = _pipRatioMax;
          } else if (entryAspectRatio < _pipRatioMin.aspectRatio) {
            pipAspectRatio = _pipRatioMin;
          } else {
            pipAspectRatio = Rational(entrySize.width.round(), entrySize.height.round());
          }

          final viewSize = MediaQuery.sizeOf(context) * MediaQuery.devicePixelRatioOf(context);
          final fittedSize = applyBoxFit(BoxFit.contain, entrySize, viewSize).destination;
          final sourceRectHint = Rectangle<int>(
            ((viewSize.width - fittedSize.width) / 2).round(),
            ((viewSize.height - fittedSize.height) / 2).round(),
            fittedSize.width.round(),
            fittedSize.height.round(),
          );

          try {
            final status = await Floating().enable(
              OnLeavePiP(
                aspectRatio: pipAspectRatio,
                sourceRectHint: sourceRectHint,
              ),
            );
            debugPrint('Enabled picture-in-picture with status=$status');
            return;
          } on PlatformException catch (e, stack) {
            await reportService.recordError(e, stack);
          }
        }
      }
    }

    debugPrint('Cancelling picture-in-picture');
    await Floating().cancelOnLeavePiP();
  }
}
