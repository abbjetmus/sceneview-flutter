class SceneViewNode {
  final String fileLocation;
  final String? name;
  final KotlinFloat3? position;
  final KotlinFloat3? rotation;
  final KotlinFloat3? scale;
  final double? scaleUnits;

  /// Enable gesture-based editing (drag, rotate, pinch-to-scale).
  /// This is the master toggle — individual axes are controlled by
  /// [isPositionEditable], [isRotationEditable], [isScaleEditable].
  final bool isEditable;
  final bool isPositionEditable;
  final bool isRotationEditable;
  final bool isScaleEditable;

  /// Clamp scale range when [isScaleEditable] is true.
  final double? editableScaleMin;
  final double? editableScaleMax;

  /// Shadow casting/receiving.
  final bool isShadowCaster;
  final bool isShadowReceiver;

  /// Whether to auto-play all animations when the model loads.
  final bool autoAnimate;

  SceneViewNode({
    required this.fileLocation,
    this.name,
    this.position,
    this.rotation,
    this.scale,
    this.scaleUnits,
    this.isEditable = false,
    this.isPositionEditable = true,
    this.isRotationEditable = true,
    this.isScaleEditable = true,
    this.editableScaleMin,
    this.editableScaleMax,
    this.isShadowCaster = true,
    this.isShadowReceiver = true,
    this.autoAnimate = true,
  });

  Map<String, dynamic> toMap() {
    final map = <String, dynamic>{
      'fileLocation': fileLocation,
      'name': name,
      'position': position?.toMap(),
      'rotation': rotation?.toMap(),
      'scale': scale?.toMap(),
      'scaleUnits': scaleUnits,
      'isEditable': isEditable,
      'isPositionEditable': isPositionEditable,
      'isRotationEditable': isRotationEditable,
      'isScaleEditable': isScaleEditable,
      'editableScaleMin': editableScaleMin,
      'editableScaleMax': editableScaleMax,
      'isShadowCaster': isShadowCaster,
      'isShadowReceiver': isShadowReceiver,
      'autoAnimate': autoAnimate,
    };
    map.removeWhere((key, value) => value == null);
    return map;
  }
}

/// Result from a hit test against real-world geometry.
class ARHitResult {
  final double x;
  final double y;
  final double z;
  final double qx;
  final double qy;
  final double qz;
  final double qw;
  final double distance;
  final bool isPlane;

  ARHitResult({
    required this.x,
    required this.y,
    required this.z,
    required this.qx,
    required this.qy,
    required this.qz,
    required this.qw,
    required this.distance,
    required this.isPlane,
  });

  factory ARHitResult.fromMap(Map<dynamic, dynamic> map) {
    return ARHitResult(
      x: (map['x'] as num).toDouble(),
      y: (map['y'] as num).toDouble(),
      z: (map['z'] as num).toDouble(),
      qx: (map['qx'] as num).toDouble(),
      qy: (map['qy'] as num).toDouble(),
      qz: (map['qz'] as num).toDouble(),
      qw: (map['qw'] as num).toDouble(),
      distance: (map['distance'] as num).toDouble(),
      isPlane: map['isPlane'] as bool,
    );
  }
}

/// Reason why AR tracking failed.
enum TrackingFailureReason {
  none,
  badState,
  insufficientLight,
  excessiveMotion,
  insufficientFeatures,
  cameraUnavailable,
}

TrackingFailureReason trackingFailureReasonFromString(String value) {
  switch (value) {
    case 'BAD_STATE':
      return TrackingFailureReason.badState;
    case 'INSUFFICIENT_LIGHT':
      return TrackingFailureReason.insufficientLight;
    case 'EXCESSIVE_MOTION':
      return TrackingFailureReason.excessiveMotion;
    case 'INSUFFICIENT_FEATURES':
      return TrackingFailureReason.insufficientFeatures;
    case 'CAMERA_UNAVAILABLE':
      return TrackingFailureReason.cameraUnavailable;
    default:
      return TrackingFailureReason.none;
  }
}

class KotlinFloat3 {
  final double x;
  final double y;
  final double z;

  KotlinFloat3({this.x = 0.0, this.y = 0.0, this.z = 0.0});

  Map<String, double> toMap() {
    return <String, double>{
      'x': x,
      'y': y,
      'z': z,
    };
  }
}
