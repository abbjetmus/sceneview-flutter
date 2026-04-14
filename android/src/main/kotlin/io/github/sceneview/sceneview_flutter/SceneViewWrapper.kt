package io.github.sceneview.sceneview_flutter

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.platform.PlatformView
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SceneViewWrapper(
    context: Context,
    private val activity: Activity,
    lifecycle: Lifecycle,
    messenger: BinaryMessenger,
    id: Int,
) : PlatformView, MethodCallHandler {
    private val TAG = "SceneViewWrapper"
    private var sceneView: ARSceneView
    private val _mainJob = SupervisorJob()
    private val _mainScope = CoroutineScope(Dispatchers.Main + _mainJob)
    private val _channel = MethodChannel(messenger, "scene_view_$id")
    private val _nodesMap = HashMap<String, ModelNode>()
    private val _anchorNodesMap = HashMap<String, AnchorNode>()
    private var _isSessionReady = false
    private var _isDisposed = false
    private var _isTracking = false
    private var _lastTrackingFailureReason: TrackingFailureReason? = null

    override fun getView(): View = sceneView

    override fun dispose() {
        _isDisposed = true
        _isSessionReady = false
        _isTracking = false
        _mainJob.cancel()
        _mainScope.cancel()
        _nodesMap.clear()
        _anchorNodesMap.values.forEach { it.detachAnchor() }
        _anchorNodesMap.clear()
    }

    init {
        sceneView = ARSceneView(
            context = context,
            sharedLifecycle = lifecycle,
            sessionConfiguration = { session, config ->
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            },
            onSessionResumed = { session ->
                _isSessionReady = true
            },
            onSessionFailed = { _isSessionReady = false },
            onTrackingFailureChanged = { reason ->
                _lastTrackingFailureReason = reason
                _mainScope.launch {
                    _channel.invokeMethod("onTrackingFailureChanged", mapOf(
                        "reason" to (reason?.name ?: "NONE")
                    ))
                }
            },
            onSessionUpdated = { session, frame ->
                val camera = frame.camera
                val wasTracking = _isTracking
                _isTracking = camera.trackingState == TrackingState.TRACKING

                if (wasTracking != _isTracking) {
                    _mainScope.launch {
                        _channel.invokeMethod("onTrackingStateChanged", mapOf(
                            "isTracking" to _isTracking
                        ))
                    }
                }
            }
        )
        sceneView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        sceneView.keepScreenOn = true
        _channel.setMethodCallHandler(this)
    }

    private suspend fun addNode(flutterNode: FlutterSceneViewNode) {
        if (_isDisposed) return

        var retries = 50
        while (retries > 0 && !_isDisposed && (!_isSessionReady || sceneView.engine == null || sceneView.modelLoader == null)) {
            delay(100)
            retries--
        }

        if (_isDisposed || sceneView.engine == null || sceneView.modelLoader == null) {
            return
        }

        val node = buildNode(flutterNode) ?: return
        if (_isDisposed) return

        // Apply gesture/editing properties
        if (flutterNode is FlutterReferenceNode) {
            node.isEditable = flutterNode.isEditable
            node.isPositionEditable = flutterNode.isPositionEditable
            node.isRotationEditable = flutterNode.isRotationEditable
            node.isScaleEditable = flutterNode.isScaleEditable
            if (flutterNode.editableScaleRange != null) {
                node.editableScaleRange = flutterNode.editableScaleRange!!
            }
            node.isShadowCaster = flutterNode.isShadowCaster
            node.isShadowReceiver = flutterNode.isShadowReceiver
        }

        sceneView.addChildNode(node)
        val nodeName = (flutterNode as? FlutterReferenceNode)?.name
        if (nodeName != null) {
            _nodesMap[nodeName] = node
        }
    }

    private fun removeNode(name: String) {
        val node = _nodesMap[name]
        if (node != null && !_isDisposed) {
            sceneView.removeChildNode(node)
            node.destroy()
            _nodesMap.remove(name)
        }
    }

    private suspend fun buildNode(flutterNode: FlutterSceneViewNode): ModelNode? {
        if (_isDisposed || sceneView.engine == null || sceneView.modelLoader == null) {
            return null
        }

        val model: ModelInstance? = when (flutterNode) {
            is FlutterReferenceNode -> {
                val fileLocation = flutterNode.fileLocation
                val isUrl = fileLocation.startsWith("http://") || fileLocation.startsWith("https://")

                try {
                    if (isUrl) {
                        sceneView.modelLoader.loadModelInstance(fileLocation)
                    } else {
                        val assetKey = Utils.getFlutterAssetKey(activity, fileLocation)
                        sceneView.modelLoader.loadModelInstance(assetKey)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading model: ${e.message}", e)
                    null
                }
            }
            else -> null
        }

        if (model == null || _isDisposed) return null

        val autoAnimate = (flutterNode as? FlutterReferenceNode)?.autoAnimate ?: true

        return ModelNode(
            modelInstance = model,
            scaleToUnits = flutterNode.scaleUnits,
            autoAnimate = autoAnimate,
        ).apply {
            worldPosition = Position(flutterNode.position.x, flutterNode.position.y, flutterNode.position.z)
            if (flutterNode.rotation.x != 0f || flutterNode.rotation.y != 0f || flutterNode.rotation.z != 0f) {
                rotation = Rotation(
                    x = flutterNode.rotation.x,
                    y = flutterNode.rotation.y,
                    z = flutterNode.rotation.z
                )
            }
            if (flutterNode.scale.x != 0f || flutterNode.scale.y != 0f || flutterNode.scale.z != 0f) {
                scale = io.github.sceneview.math.Scale(
                    x = flutterNode.scale.x,
                    y = flutterNode.scale.y,
                    z = flutterNode.scale.z
                )
            }
        }
    }

    // --- Animation methods ---

    private fun playAnimation(name: String, animationIndex: Int, speed: Float, loop: Boolean) {
        val node = _nodesMap[name] ?: return
        node.playAnimation(animationIndex, speed, loop)
    }

    private fun stopAnimation(name: String, animationIndex: Int) {
        val node = _nodesMap[name] ?: return
        node.stopAnimation(animationIndex)
    }

    private fun setAnimationSpeed(name: String, animationIndex: Int, speed: Float) {
        val node = _nodesMap[name] ?: return
        node.setAnimationSpeed(animationIndex, speed)
    }

    private fun getAnimationCount(name: String): Int {
        val node = _nodesMap[name] ?: return 0
        return node.animationCount
    }

    // --- Plane renderer methods ---

    private fun setPlaneRendererEnabled(enabled: Boolean) {
        sceneView.planeRenderer.isEnabled = enabled
    }

    private fun setPlaneRendererVisible(visible: Boolean) {
        sceneView.planeRenderer.isVisible = visible
    }

    // --- Depth occlusion ---

    private fun setDepthOcclusionEnabled(enabled: Boolean) {
        sceneView.cameraStream?.isDepthOcclusionEnabled = enabled
    }

    // --- Hit testing ---

    private fun hitTest(xPx: Float, yPx: Float): Map<String, Any>? {
        if (!_isSessionReady || !_isTracking) return null

        val firstHit = sceneView.hitTestAR(
            xPx = xPx,
            yPx = yPx,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING, Plane.Type.HORIZONTAL_DOWNWARD_FACING, Plane.Type.VERTICAL),
            point = true,
            depthPoint = true,
        ) ?: return null

        val pose = firstHit.hitPose
        return mapOf(
            "x" to pose.tx().toDouble(),
            "y" to pose.ty().toDouble(),
            "z" to pose.tz().toDouble(),
            "qx" to pose.qx().toDouble(),
            "qy" to pose.qy().toDouble(),
            "qz" to pose.qz().toDouble(),
            "qw" to pose.qw().toDouble(),
            "distance" to firstHit.distance.toDouble(),
            "isPlane" to (firstHit.trackable is Plane),
        )
    }

    // --- Anchor nodes ---

    private suspend fun addAnchorNode(flutterNode: FlutterSceneViewNode, xPx: Float, yPx: Float) {
        if (_isDisposed || !_isSessionReady || !_isTracking) return

        var retries = 50
        while (retries > 0 && !_isDisposed && (sceneView.engine == null || sceneView.modelLoader == null)) {
            delay(100)
            retries--
        }

        if (_isDisposed || sceneView.engine == null || sceneView.modelLoader == null) return

        val firstHit = sceneView.hitTestAR(
            xPx = xPx,
            yPx = yPx,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING, Plane.Type.HORIZONTAL_DOWNWARD_FACING, Plane.Type.VERTICAL),
            point = true,
            depthPoint = true,
        ) ?: return

        val modelNode = buildNode(flutterNode) ?: return
        if (_isDisposed) return

        if (flutterNode is FlutterReferenceNode) {
            modelNode.isEditable = flutterNode.isEditable
            modelNode.isPositionEditable = flutterNode.isPositionEditable
            modelNode.isRotationEditable = flutterNode.isRotationEditable
            modelNode.isScaleEditable = flutterNode.isScaleEditable
            modelNode.isShadowCaster = flutterNode.isShadowCaster
            modelNode.isShadowReceiver = flutterNode.isShadowReceiver
        }

        val anchor = firstHit.createAnchor() ?: return
        val engine = sceneView.engine ?: return
        val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
            addChildNode(modelNode)
        }

        sceneView.addChildNode(anchorNode)
        val nodeName = (flutterNode as? FlutterReferenceNode)?.name
        if (nodeName != null) {
            _nodesMap[nodeName] = modelNode
            _anchorNodesMap[nodeName] = anchorNode
        }
    }

    private fun removeAnchorNode(name: String) {
        val anchorNode = _anchorNodesMap[name]
        val modelNode = _nodesMap[name]
        if (anchorNode != null && !_isDisposed) {
            sceneView.removeChildNode(anchorNode)
            anchorNode.detachAnchor()
            anchorNode.destroy()
            _anchorNodesMap.remove(name)
        }
        if (modelNode != null) {
            modelNode.destroy()
            _nodesMap.remove(name)
        }
    }

    // --- Update node transform ---

    private fun updateNodePosition(name: String, x: Float, y: Float, z: Float) {
        val node = _nodesMap[name] ?: return
        node.worldPosition = Position(x, y, z)
    }

    private fun updateNodeRotation(name: String, x: Float, y: Float, z: Float) {
        val node = _nodesMap[name] ?: return
        node.rotation = Rotation(x, y, z)
    }

    private fun updateNodeScale(name: String, x: Float, y: Float, z: Float) {
        val node = _nodesMap[name] ?: return
        node.scale = io.github.sceneview.math.Scale(x, y, z)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "init" -> result.success(null)

            "addNode" -> {
                val flutterNode = FlutterSceneViewNode.from(call.arguments as Map<String, *>)
                _mainScope.launch { addNode(flutterNode) }
                result.success(null)
            }

            "removeNode" -> {
                val name = call.argument<String>("name")
                if (name != null) {
                    removeNode(name)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "Name is required", null)
                }
            }

            // Animation
            "playAnimation" -> {
                val name = call.argument<String>("name") ?: ""
                val index = call.argument<Int>("animationIndex") ?: 0
                val speed = (call.argument<Double>("speed") ?: 1.0).toFloat()
                val loop = call.argument<Boolean>("loop") ?: true
                playAnimation(name, index, speed, loop)
                result.success(null)
            }

            "stopAnimation" -> {
                val name = call.argument<String>("name") ?: ""
                val index = call.argument<Int>("animationIndex") ?: 0
                stopAnimation(name, index)
                result.success(null)
            }

            "setAnimationSpeed" -> {
                val name = call.argument<String>("name") ?: ""
                val index = call.argument<Int>("animationIndex") ?: 0
                val speed = (call.argument<Double>("speed") ?: 1.0).toFloat()
                setAnimationSpeed(name, index, speed)
                result.success(null)
            }

            "getAnimationCount" -> {
                val name = call.argument<String>("name") ?: ""
                result.success(getAnimationCount(name))
            }

            // Plane renderer
            "setPlaneRendererEnabled" -> {
                val enabled = call.argument<Boolean>("enabled") ?: true
                setPlaneRendererEnabled(enabled)
                result.success(null)
            }

            "setPlaneRendererVisible" -> {
                val visible = call.argument<Boolean>("visible") ?: true
                setPlaneRendererVisible(visible)
                result.success(null)
            }

            // Depth occlusion
            "setDepthOcclusionEnabled" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                setDepthOcclusionEnabled(enabled)
                result.success(null)
            }

            // Hit testing
            "hitTest" -> {
                val x = (call.argument<Double>("x") ?: 0.0).toFloat()
                val y = (call.argument<Double>("y") ?: 0.0).toFloat()
                val hitResult = hitTest(x, y)
                result.success(hitResult)
            }

            // Anchor nodes
            "addAnchorNode" -> {
                val args = call.arguments as Map<String, *>
                val flutterNode = FlutterSceneViewNode.from(args)
                val xPx = (args["hitTestX"] as? Double ?: 0.0).toFloat()
                val yPx = (args["hitTestY"] as? Double ?: 0.0).toFloat()
                _mainScope.launch { addAnchorNode(flutterNode, xPx, yPx) }
                result.success(null)
            }

            "removeAnchorNode" -> {
                val name = call.argument<String>("name")
                if (name != null) {
                    removeAnchorNode(name)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "Name is required", null)
                }
            }

            // Update node transforms
            "updateNodePosition" -> {
                val name = call.argument<String>("name") ?: ""
                val x = (call.argument<Double>("x") ?: 0.0).toFloat()
                val y = (call.argument<Double>("y") ?: 0.0).toFloat()
                val z = (call.argument<Double>("z") ?: 0.0).toFloat()
                updateNodePosition(name, x, y, z)
                result.success(null)
            }

            "updateNodeRotation" -> {
                val name = call.argument<String>("name") ?: ""
                val x = (call.argument<Double>("x") ?: 0.0).toFloat()
                val y = (call.argument<Double>("y") ?: 0.0).toFloat()
                val z = (call.argument<Double>("z") ?: 0.0).toFloat()
                updateNodeRotation(name, x, y, z)
                result.success(null)
            }

            "updateNodeScale" -> {
                val name = call.argument<String>("name") ?: ""
                val x = (call.argument<Double>("x") ?: 0.0).toFloat()
                val y = (call.argument<Double>("y") ?: 0.0).toFloat()
                val z = (call.argument<Double>("z") ?: 0.0).toFloat()
                updateNodeScale(name, x, y, z)
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }
}
