import 'package:sceneview_flutter/sceneview_flutter_platform_interface.dart';
import 'package:sceneview_flutter/sceneview_node.dart';

class SceneViewController {
  SceneViewController._({
    required this.sceneId,
  });

  final int sceneId;

  static Future<SceneViewController> init(
    int sceneId,
  ) async {
    await SceneviewFlutterPlatform.instance.init(sceneId);
    return SceneViewController._(sceneId: sceneId);
  }

  // --- Node management ---

  void addNode(SceneViewNode node) {
    SceneviewFlutterPlatform.instance.addNode(sceneId, node);
  }

  void removeNode({required String name}) {
    SceneviewFlutterPlatform.instance.removeNode(sceneId, name);
  }

  // --- Tracking callbacks ---

  /// Listen for tracking state changes (tracking / not tracking).
  void onTrackingStateChanged(void Function(bool isTracking)? callback) {
    SceneviewFlutterPlatform.instance.setTrackingStateCallback(sceneId, callback);
  }

  /// Listen for tracking failure reasons (e.g. insufficient light, excessive motion).
  void onTrackingFailureChanged(void Function(TrackingFailureReason reason)? callback) {
    SceneviewFlutterPlatform.instance.setTrackingFailureCallback(sceneId, callback);
  }

  // --- Animation ---

  /// Play an animation on the named node.
  void playAnimation(String name, {int animationIndex = 0, double speed = 1.0, bool loop = true}) {
    SceneviewFlutterPlatform.instance.playAnimation(sceneId, name, animationIndex: animationIndex, speed: speed, loop: loop);
  }

  /// Stop an animation on the named node.
  void stopAnimation(String name, {int animationIndex = 0}) {
    SceneviewFlutterPlatform.instance.stopAnimation(sceneId, name, animationIndex: animationIndex);
  }

  /// Set the playback speed for an animation. Negative values play in reverse.
  void setAnimationSpeed(String name, {int animationIndex = 0, double speed = 1.0}) {
    SceneviewFlutterPlatform.instance.setAnimationSpeed(sceneId, name, animationIndex: animationIndex, speed: speed);
  }

  /// Get the number of animations available on the named node.
  Future<int> getAnimationCount(String name) {
    return SceneviewFlutterPlatform.instance.getAnimationCount(sceneId, name);
  }

  // --- Plane renderer ---

  /// Enable or disable plane detection and rendering.
  void setPlaneRendererEnabled(bool enabled) {
    SceneviewFlutterPlatform.instance.setPlaneRendererEnabled(sceneId, enabled);
  }

  /// Show or hide detected plane overlays without disabling detection.
  void setPlaneRendererVisible(bool visible) {
    SceneviewFlutterPlatform.instance.setPlaneRendererVisible(sceneId, visible);
  }

  // --- Depth occlusion ---

  /// Enable depth occlusion so virtual objects are occluded by real-world geometry.
  /// Requires a device that supports ARCore depth (ToF sensor or software depth).
  void setDepthOcclusionEnabled(bool enabled) {
    SceneviewFlutterPlatform.instance.setDepthOcclusionEnabled(sceneId, enabled);
  }

  // --- Hit testing ---

  /// Perform a hit test at the given screen coordinates.
  /// Returns the hit result with world position and orientation, or null if nothing was hit.
  Future<ARHitResult?> hitTest(double x, double y) {
    return SceneviewFlutterPlatform.instance.hitTest(sceneId, x, y);
  }

  // --- Anchor nodes ---

  /// Place a 3D model anchored to a real-world surface detected at the given screen coordinates.
  /// The model will stay fixed in the real world as the user moves around.
  void addAnchorNode(SceneViewNode node, {required double hitTestX, required double hitTestY}) {
    SceneviewFlutterPlatform.instance.addAnchorNode(sceneId, node, hitTestX, hitTestY);
  }

  /// Remove an anchored node by name.
  void removeAnchorNode({required String name}) {
    SceneviewFlutterPlatform.instance.removeAnchorNode(sceneId, name);
  }

  // --- Update node transforms ---

  /// Update the world position of an existing node.
  void updateNodePosition(String name, {required double x, required double y, required double z}) {
    SceneviewFlutterPlatform.instance.updateNodePosition(sceneId, name, x, y, z);
  }

  /// Update the rotation of an existing node (in degrees).
  void updateNodeRotation(String name, {required double x, required double y, required double z}) {
    SceneviewFlutterPlatform.instance.updateNodeRotation(sceneId, name, x, y, z);
  }

  /// Update the scale of an existing node.
  void updateNodeScale(String name, {required double x, required double y, required double z}) {
    SceneviewFlutterPlatform.instance.updateNodeScale(sceneId, name, x, y, z);
  }

  void dispose() {
    SceneviewFlutterPlatform.instance.dispose(sceneId);
  }
}
