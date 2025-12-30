class SceneViewNode {
  final String fileLocation;
  final String? name;
  final KotlinFloat3? position;
  final KotlinFloat3? rotation;
  final KotlinFloat3? scale;
  final double? scaleUnits;

  SceneViewNode({
    required this.fileLocation,
    this.name,
    this.position,
    this.rotation,
    this.scale,
    this.scaleUnits,
  });

  Map<String, dynamic> toMap() {
    final map = {
      'fileLocation': fileLocation,
      'name': name,
      'position': position?.toMap(),
      'rotation': rotation?.toMap(),
      'scale': scale?.toMap(),
      'scaleUnits': scaleUnits,
    };
    map.removeWhere((key, value) => value == null);
    return map;
  }
}

class KotlinFloat3 {
  final double x;
  final double y;
  final double z;

  KotlinFloat3({this.x = 0.0, this.y = 0.0, this.z = 0.0});

  toMap() {
    return <String, double>{
      'x': x,
      'y': y,
      'z': z,
    };
  }
}
