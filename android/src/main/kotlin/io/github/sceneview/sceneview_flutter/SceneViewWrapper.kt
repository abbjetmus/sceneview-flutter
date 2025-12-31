package io.github.sceneview.sceneview_flutter

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.google.ar.core.Config
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.platform.PlatformView
import io.github.sceneview.ar.ARSceneView
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
    private var _isSessionReady = false
    private var _isDisposed = false

    override fun getView(): View = sceneView

    override fun dispose() {
        _isDisposed = true
        _isSessionReady = false
        _mainJob.cancel()
        _mainScope.cancel()
        _nodesMap.clear()
    }

    init {
        sceneView = ARSceneView(context, sharedLifecycle = lifecycle)
        sceneView.apply {
            configureSession { session, config ->
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            }
            onSessionResumed = { _isSessionReady = true }
            onSessionFailed = { _isSessionReady = false }
        }
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

        return ModelNode(modelInstance = model, scaleToUnits = flutterNode.scaleUnits).apply {
            worldPosition = Position(flutterNode.position.x, flutterNode.position.y, flutterNode.position.z)
            if (flutterNode.rotation.x != 0f || flutterNode.rotation.y != 0f || flutterNode.rotation.z != 0f) {
                rotation = Rotation(
                    x = flutterNode.rotation.x,
                    y = flutterNode.rotation.y,
                    z = flutterNode.rotation.z
                )
            }
        }
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
            else -> result.notImplemented()
        }
    }
}
