import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:sceneview_flutter/sceneview_node.dart';

import 'sceneview_flutter_method_channel.dart';

typedef TrackingStateCallback = void Function(bool isTracking);
typedef TrackingFailureCallback = void Function(TrackingFailureReason reason);

abstract class SceneviewFlutterPlatform extends PlatformInterface {
  SceneviewFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static SceneviewFlutterPlatform _instance = MethodChannelSceneViewFlutter();

  static SceneviewFlutterPlatform get instance => _instance;

  static set instance(SceneviewFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> init(int sceneId) {
    throw UnimplementedError('init() has not been implemented.');
  }

  void addNode(int sceneId, SceneViewNode node) {
    throw UnimplementedError('addNode() has not been implemented.');
  }

  void removeNode(int sceneId, String name) {
    throw UnimplementedError('removeNode() has not been implemented.');
  }

  void dispose(int sceneId) {
    throw UnimplementedError('dispose() has not been implemented.');
  }

  // --- Callbacks ---

  void setTrackingStateCallback(int sceneId, TrackingStateCallback? callback) {
    throw UnimplementedError('setTrackingStateCallback() has not been implemented.');
  }

  void setTrackingFailureCallback(int sceneId, TrackingFailureCallback? callback) {
    throw UnimplementedError('setTrackingFailureCallback() has not been implemented.');
  }

  // --- Animation ---

  void playAnimation(int sceneId, String name, {int animationIndex = 0, double speed = 1.0, bool loop = true}) {
    throw UnimplementedError('playAnimation() has not been implemented.');
  }

  void stopAnimation(int sceneId, String name, {int animationIndex = 0}) {
    throw UnimplementedError('stopAnimation() has not been implemented.');
  }

  void setAnimationSpeed(int sceneId, String name, {int animationIndex = 0, double speed = 1.0}) {
    throw UnimplementedError('setAnimationSpeed() has not been implemented.');
  }

  Future<int> getAnimationCount(int sceneId, String name) {
    throw UnimplementedError('getAnimationCount() has not been implemented.');
  }

  // --- Plane renderer ---

  void setPlaneRendererEnabled(int sceneId, bool enabled) {
    throw UnimplementedError('setPlaneRendererEnabled() has not been implemented.');
  }

  void setPlaneRendererVisible(int sceneId, bool visible) {
    throw UnimplementedError('setPlaneRendererVisible() has not been implemented.');
  }

  // --- Depth occlusion ---

  void setDepthOcclusionEnabled(int sceneId, bool enabled) {
    throw UnimplementedError('setDepthOcclusionEnabled() has not been implemented.');
  }

  // --- Hit testing ---

  Future<ARHitResult?> hitTest(int sceneId, double x, double y) {
    throw UnimplementedError('hitTest() has not been implemented.');
  }

  // --- Anchor nodes ---

  void addAnchorNode(int sceneId, SceneViewNode node, double hitTestX, double hitTestY) {
    throw UnimplementedError('addAnchorNode() has not been implemented.');
  }

  void removeAnchorNode(int sceneId, String name) {
    throw UnimplementedError('removeAnchorNode() has not been implemented.');
  }

  // --- Update node transforms ---

  void updateNodePosition(int sceneId, String name, double x, double y, double z) {
    throw UnimplementedError('updateNodePosition() has not been implemented.');
  }

  void updateNodeRotation(int sceneId, String name, double x, double y, double z) {
    throw UnimplementedError('updateNodeRotation() has not been implemented.');
  }

  void updateNodeScale(int sceneId, String name, double x, double y, double z) {
    throw UnimplementedError('updateNodeScale() has not been implemented.');
  }
}
