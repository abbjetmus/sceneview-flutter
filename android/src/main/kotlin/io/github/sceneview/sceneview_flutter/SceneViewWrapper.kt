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
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val _mainScope = CoroutineScope(Dispatchers.Main)
    private val _channel = MethodChannel(messenger, "scene_view_$id")
    private val _nodesMap = HashMap<String, ModelNode>()
    private var _isSessionReady = false

    override fun getView(): View {
        Log.i(TAG, "getView:")
        return sceneView
    }

    override fun dispose() {
        Log.i(TAG, "dispose")
        _isSessionReady = false
        _nodesMap.clear()
    }

    init {
        Log.i(TAG, "init")
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
            onSessionResumed = { session ->
                Log.i(TAG, "onSessionResumed - session is active now")
                _isSessionReady = true
            }
            onSessionFailed = { exception ->
                Log.e(TAG, "onSessionFailed : $exception")
                _isSessionReady = false
            }
            onSessionCreated = { session ->
                Log.i(TAG, "onSessionCreated")
            }
            onTrackingFailureChanged = { reason ->
                Log.i(TAG, "onTrackingFailureChanged: $reason");
            }
        }
        sceneView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        sceneView.keepScreenOn = true
        _channel.setMethodCallHandler(this)
    }

    private suspend fun addNode(flutterNode: FlutterSceneViewNode) {
        // Wait for session to be ready before loading models
        var retries = 50  // Wait up to 5 seconds
        while (!_isSessionReady && retries > 0) {
            delay(100)
            retries--
        }
        
        if (!_isSessionReady) {
            Log.w(TAG, "Session not ready after waiting, cannot add node")
            return
        }
        
        // Additional check for engine and modelLoader
        if (sceneView.engine == null || sceneView.modelLoader == null) {
            Log.w(TAG, "Engine or modelLoader is null even though session is ready")
            return
        }
        
        val node = buildNode(flutterNode) ?: run {
            Log.w(TAG, "Failed to build node")
            return
        }
        sceneView.addChildNode(node)
        val nodeName = (flutterNode as? FlutterReferenceNode)?.name
        if (nodeName != null) {
            _nodesMap[nodeName] = node
        }
        Log.d(TAG, "Node added: $nodeName")
    }

    private fun removeNode(name: String) {
        val node = _nodesMap[name]
        if (node != null) {
            sceneView.removeChildNode(node)
            _nodesMap.remove(name)
            Log.d(TAG, "Node removed: $name")
        } else {
            Log.w(TAG, "Node not found for removal: $name")
        }
    }

    private suspend fun buildNode(flutterNode: FlutterSceneViewNode): ModelNode? {
        // Ensure engine and modelLoader are ready before loading models
        if (sceneView.engine == null || sceneView.modelLoader == null) {
            Log.w(TAG, "Cannot load model: engine or modelLoader is not ready yet")
            return null
        }
        
        var model: ModelInstance? = null

        when (flutterNode) {
            is FlutterReferenceNode -> {
                val fileLocation = flutterNode.fileLocation
                // Check if it's a URL (starts with http:// or https://)
                val isUrl = fileLocation.startsWith("http://") || fileLocation.startsWith("https://")
                
                try {
                    if (isUrl) {
                        // Load from URL directly
                        Log.d(TAG, "Loading model from URL: $fileLocation")
                        model = sceneView.modelLoader?.loadModelInstance(fileLocation)
                    } else {
                        // Load from Flutter asset
                        val assetKey = Utils.getFlutterAssetKey(activity, fileLocation)
                        Log.d(TAG, "Loading model from asset: $assetKey")
                        model = sceneView.modelLoader?.loadModelInstance(assetKey)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading model: ${e.message}", e)
                    return null
                }
            }
        }
        if (model != null) {
            val modelNode = ModelNode(modelInstance = model, scaleToUnits = flutterNode.scaleUnits).apply {
                transform(
                    position = flutterNode.position,
                    rotation = flutterNode.rotation,
                )
            }
            return modelNode
        }
        return null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "init" -> {
                result.success(null)
            }

            "addNode" -> {
                Log.i(TAG, "addNode")
                val flutterNode = FlutterSceneViewNode.from(call.arguments as Map<String, *>)
                _mainScope.launch {
                    addNode(flutterNode)
                }
                result.success(null)
                return
            }

            "removeNode" -> {
                val name = call.argument<String>("name")
                if (name != null) {
                    removeNode(name)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "Name is required", null)
                }
                return
            }

            else -> result.notImplemented()
        }
    }
}