import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:sceneview_flutter/sceneview_controller.dart';
import 'package:sceneview_flutter/sceneview_node.dart';

class SceneView extends StatefulWidget {
  const SceneView({
    super.key,
    this.onViewCreated,
    this.onTrackingStateChanged,
    this.onTrackingFailureChanged,
  });

  final Function(SceneViewController)? onViewCreated;

  /// Called when AR tracking state changes between tracking and not tracking.
  final void Function(bool isTracking)? onTrackingStateChanged;

  /// Called when the tracking failure reason changes.
  /// Provides the specific reason (e.g. insufficient light, excessive motion)
  /// so the app can show appropriate user guidance.
  final void Function(TrackingFailureReason reason)? onTrackingFailureChanged;

  @override
  State<SceneView> createState() => _SceneViewState();
}

class _SceneViewState extends State<SceneView> {
  final Completer<SceneViewController> _controller =
      Completer<SceneViewController>();

  @override
  Widget build(BuildContext context) {
    // This is used in the platform side to register the view.
    const String viewType = 'SceneView';
    // Pass parameters to the platform side.
    const Map<String, dynamic> creationParams = <String, dynamic>{};

    return PlatformViewLink(
      viewType: viewType,
      surfaceFactory: (context, controller) {
        return AndroidViewSurface(
          controller: controller as AndroidViewController,
          gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
          hitTestBehavior: PlatformViewHitTestBehavior.opaque,
        );
      },
      onCreatePlatformView: (params) {
        return PlatformViewsService.initExpensiveAndroidView(
          id: params.id,
          viewType: viewType,
          layoutDirection: TextDirection.ltr,
          creationParams: creationParams,
          creationParamsCodec: const StandardMessageCodec(),
          onFocus: () {
            params.onFocusChanged(true);
          },
        )
          ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
          ..addOnPlatformViewCreatedListener((id) {
            onPlatformViewCreated(id);
          });
      },
    );
  }

  Future<void> onPlatformViewCreated(int id) async {
    final controller = await SceneViewController.init(id);

    // Wire up callbacks
    if (widget.onTrackingStateChanged != null) {
      controller.onTrackingStateChanged(widget.onTrackingStateChanged);
    }
    if (widget.onTrackingFailureChanged != null) {
      controller.onTrackingFailureChanged(widget.onTrackingFailureChanged);
    }

    _controller.complete(controller);
    widget.onViewCreated?.call(controller);
  }

  @override
  void dispose() {
    _disposeController();
    super.dispose();
  }

  Future<void> _disposeController() async {
    final SceneViewController controller = await _controller.future;
    controller.dispose();
  }
}
