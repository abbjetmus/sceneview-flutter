import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:sceneview_flutter/sceneview_node.dart';

import 'sceneview_flutter_platform_interface.dart';

/// An implementation of [SceneviewFlutterPlatform] that uses method channels.
class MethodChannelSceneViewFlutter extends SceneviewFlutterPlatform {
  /// Registers the Android implementation of SceneviewFlutterPlatform.
  static void registerWith() {
    SceneviewFlutterPlatform.instance = MethodChannelSceneViewFlutter();
  }

  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('sceneview_flutter');

  final Map<int, MethodChannel> _channels = {};
  final Map<int, TrackingStateCallback?> _trackingStateCallbacks = {};
  final Map<int, TrackingFailureCallback?> _trackingFailureCallbacks = {};

  MethodChannel ensureChannelInitialized(int sceneId) {
    if (!_channels.containsKey(sceneId)) {
      final channel = MethodChannel('scene_view_$sceneId');
      channel.setMethodCallHandler(
          (MethodCall call) => _handleMethodCall(call, sceneId));
      _channels[sceneId] = channel;
    }
    return _channels[sceneId]!;
  }

  @override
  Future<void> init(int sceneId) async {
    final channel = ensureChannelInitialized(sceneId);
    return channel.invokeMethod<void>('init');
  }

  @override
  void addNode(int sceneId, SceneViewNode node) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('addNode', node.toMap());
  }

  @override
  void removeNode(int sceneId, String name) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('removeNode', {'name': name});
  }

  // --- Callbacks ---

  @override
  void setTrackingStateCallback(int sceneId, TrackingStateCallback? callback) {
    _trackingStateCallbacks[sceneId] = callback;
  }

  @override
  void setTrackingFailureCallback(int sceneId, TrackingFailureCallback? callback) {
    _trackingFailureCallbacks[sceneId] = callback;
  }

  // --- Animation ---

  @override
  void playAnimation(int sceneId, String name, {int animationIndex = 0, double speed = 1.0, bool loop = true}) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('playAnimation', {
      'name': name,
      'animationIndex': animationIndex,
      'speed': speed,
      'loop': loop,
    });
  }

  @override
  void stopAnimation(int sceneId, String name, {int animationIndex = 0}) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('stopAnimation', {
      'name': name,
      'animationIndex': animationIndex,
    });
  }

  @override
  void setAnimationSpeed(int sceneId, String name, {int animationIndex = 0, double speed = 1.0}) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('setAnimationSpeed', {
      'name': name,
      'animationIndex': animationIndex,
      'speed': speed,
    });
  }

  @override
  Future<int> getAnimationCount(int sceneId, String name) async {
    final channel = ensureChannelInitialized(sceneId);
    final count = await channel.invokeMethod<int>('getAnimationCount', {'name': name});
    return count ?? 0;
  }

  // --- Plane renderer ---

  @override
  void setPlaneRendererEnabled(int sceneId, bool enabled) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('setPlaneRendererEnabled', {'enabled': enabled});
  }

  @override
  void setPlaneRendererVisible(int sceneId, bool visible) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('setPlaneRendererVisible', {'visible': visible});
  }

  // --- Depth occlusion ---

  @override
  void setDepthOcclusionEnabled(int sceneId, bool enabled) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('setDepthOcclusionEnabled', {'enabled': enabled});
  }

  // --- Hit testing ---

  @override
  Future<ARHitResult?> hitTest(int sceneId, double x, double y) async {
    final channel = ensureChannelInitialized(sceneId);
    final result = await channel.invokeMethod<Map<dynamic, dynamic>>('hitTest', {
      'x': x,
      'y': y,
    });
    if (result == null) return null;
    return ARHitResult.fromMap(result);
  }

  // --- Anchor nodes ---

  @override
  void addAnchorNode(int sceneId, SceneViewNode node, double hitTestX, double hitTestY) {
    final channel = ensureChannelInitialized(sceneId);
    final map = node.toMap();
    map['hitTestX'] = hitTestX;
    map['hitTestY'] = hitTestY;
    channel.invokeMethod('addAnchorNode', map);
  }

  @override
  void removeAnchorNode(int sceneId, String name) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('removeAnchorNode', {'name': name});
  }

  // --- Update node transforms ---

  @override
  void updateNodePosition(int sceneId, String name, double x, double y, double z) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('updateNodePosition', {'name': name, 'x': x, 'y': y, 'z': z});
  }

  @override
  void updateNodeRotation(int sceneId, String name, double x, double y, double z) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('updateNodeRotation', {'name': name, 'x': x, 'y': y, 'z': z});
  }

  @override
  void updateNodeScale(int sceneId, String name, double x, double y, double z) {
    final channel = ensureChannelInitialized(sceneId);
    channel.invokeMethod('updateNodeScale', {'name': name, 'x': x, 'y': y, 'z': z});
  }

  Future<dynamic> _handleMethodCall(MethodCall call, int sceneId) async {
    switch (call.method) {
      case 'onTrackingStateChanged':
        final isTracking = call.arguments['isTracking'] as bool;
        _trackingStateCallbacks[sceneId]?.call(isTracking);
        break;
      case 'onTrackingFailureChanged':
        final reasonStr = call.arguments['reason'] as String;
        final reason = trackingFailureReasonFromString(reasonStr);
        _trackingFailureCallbacks[sceneId]?.call(reason);
        break;
      default:
        throw MissingPluginException();
    }
  }

  @override
  void dispose(int sceneId) {
    _channels.remove(sceneId);
    _trackingStateCallbacks.remove(sceneId);
    _trackingFailureCallbacks.remove(sceneId);
  }
}
