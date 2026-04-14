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

                // Gesture/editing properties
                val isEditable = map["isEditable"] as? Boolean ?: false
                val isPositionEditable = map["isPositionEditable"] as? Boolean ?: true
                val isRotationEditable = map["isRotationEditable"] as? Boolean ?: true
                val isScaleEditable = map["isScaleEditable"] as? Boolean ?: true
                val editableScaleMin = (map["editableScaleMin"] as? Double)?.toFloat()
                val editableScaleMax = (map["editableScaleMax"] as? Double)?.toFloat()
                val editableScaleRange = if (editableScaleMin != null && editableScaleMax != null) {
                    editableScaleMin..editableScaleMax
                } else null

                // Shadow properties
                val isShadowCaster = map["isShadowCaster"] as? Boolean ?: true
                val isShadowReceiver = map["isShadowReceiver"] as? Boolean ?: true

                // Animation
                val autoAnimate = map["autoAnimate"] as? Boolean ?: true

                return FlutterReferenceNode(
                    fileLocation = fileLocation,
                    name = name,
                    position = p.position,
                    rotation = r.rotation,
                    scale = s.scale,
                    scaleUnits = scaleUnits,
                    isEditable = isEditable,
                    isPositionEditable = isPositionEditable,
                    isRotationEditable = isRotationEditable,
                    isScaleEditable = isScaleEditable,
                    editableScaleRange = editableScaleRange,
                    isShadowCaster = isShadowCaster,
                    isShadowReceiver = isShadowReceiver,
                    autoAnimate = autoAnimate,
                )
            }
            throw IllegalArgumentException("fileLocation is required to create a FlutterSceneViewNode")
        }
    }
}


class FlutterReferenceNode(
    val fileLocation: String,
    val name: String?,
    position: Float3,
    rotation: Float3,
    scale: Float3,
    scaleUnits: Float,
    val isEditable: Boolean = false,
    val isPositionEditable: Boolean = true,
    val isRotationEditable: Boolean = true,
    val isScaleEditable: Boolean = true,
    val editableScaleRange: ClosedFloatingPointRange<Float>? = null,
    val isShadowCaster: Boolean = true,
    val isShadowReceiver: Boolean = true,
    val autoAnimate: Boolean = true,
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
