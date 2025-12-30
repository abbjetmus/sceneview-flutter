package io.github.sceneview.sceneview_flutter

import dev.romainguy.kotlin.math.Float3

abstract class FlutterSceneViewNode(
    val position: Float3 = Float3(0f, 0f, 0f),
    val rotation: Float3 = Float3(0f, 0f, 0f),
    val scale: Float3 = Float3(0f, 0f, 0f),
    val scaleUnits: Float = 1.0f,
) {

    companion object {
        fun from(map: Map<String, *>): FlutterSceneViewNode {
            val fileLocation = map["fileLocation"] as String?
            if (fileLocation != null) {
                val name = map["name"] as String?
                val p = FlutterPosition.from(map["position"] as Map<String, *>?)
                val r = FlutterRotation.from(map["rotation"] as Map<String, *>?)
                val s = FlutterScale.from(map["scale"] as Map<String, *>?)
                val scaleUnits = (map["scaleUnits"] as? Double)?.toFloat() ?: 1.0f
                return FlutterReferenceNode(
                    fileLocation,
                    name,
                    p.position,
                    r.rotation,
                    s.scale,
                    scaleUnits,
                )
            }
            throw Exception()
        }
    }
}


class FlutterReferenceNode(
    val fileLocation: String,
    val name: String?,
    position: Float3,
    rotation: Float3,
    scale: Float3,
    scaleUnits: Float
) :
    FlutterSceneViewNode(position, rotation, scale, scaleUnits)

class FlutterPosition(val position: Float3) {
    companion object {
        fun from(map: Map<String, *>?): FlutterPosition {
            if (map == null) {
                return FlutterPosition(Float3(0f, 0f, 0f))
            }
            val x = ((map["x"] as? Double) ?: 0.0).toFloat()
            val y = ((map["y"] as? Double) ?: 0.0).toFloat()
            val z = ((map["z"] as? Double) ?: 0.0).toFloat()
            return FlutterPosition(Float3(x, y, z))
        }
    }
}

class FlutterRotation(val rotation: Float3) {
    companion object {
        fun from(map: Map<String, *>?): FlutterRotation {
            if (map == null) {
                return FlutterRotation(Float3(0f, 0f, 0f))
            }
            val x = ((map["x"] as? Double) ?: 0.0).toFloat()
            val y = ((map["y"] as? Double) ?: 0.0).toFloat()
            val z = ((map["z"] as? Double) ?: 0.0).toFloat()
            return FlutterRotation(Float3(x, y, z))
        }
    }
}

class FlutterScale(val scale: Float3) {
    companion object {
        fun from(map: Map<String, *>?): FlutterScale {
            if (map == null) {
                return FlutterScale(Float3(1f, 1f, 1f))
            }
            val x = ((map["x"] as? Double) ?: 1.0).toFloat()
            val y = ((map["y"] as? Double) ?: 1.0).toFloat()
            val z = ((map["z"] as? Double) ?: 1.0).toFloat()
            return FlutterScale(Float3(x, y, z))
        }
    }
}