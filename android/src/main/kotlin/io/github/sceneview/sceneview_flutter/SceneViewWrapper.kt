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
import kotlinx.coroutines.Job
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

    override fun getView(): View {
        Log.i(TAG, "getView:")
        return sceneView
    }

    override fun dispose() {
        Log.i(TAG, "dispose")
        _isDisposed = true
        _isSessionReady = false
        
        // Cancel all coroutines first to stop any ongoing operations
        _mainJob.cancel()
        _mainScope.cancel()
        
        // Don't try to remove nodes - the scene is being disposed and will handle cleanup
        // Just clear our references to allow garbage collection
        if (!_nodesMap.isEmpty()) {
            Log.d(TAG, "Disposing: ${_nodesMap.size} nodes in map - clearing references (scene will handle cleanup)")
        }
        _nodesMap.clear()
        
        Log.i(TAG, "dispose completed")
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
                Log.i(TAG, "onSessionResumed CALLBACK FIRED - session is active now, engine=${sceneView.engine != null}, modelLoader=${sceneView.modelLoader != null}")
                _isSessionReady = true
                Log.i(TAG, "_isSessionReady set to true")
            }
            onSessionFailed = { exception ->
                Log.e(TAG, "onSessionFailed CALLBACK FIRED: $exception")
                _isSessionReady = false
                Log.e(TAG, "_isSessionReady set to false due to session failure")
            }
            onSessionCreated = { session ->
                Log.i(TAG, "onSessionCreated CALLBACK FIRED - engine=${sceneView.engine != null}, modelLoader=${sceneView.modelLoader != null}")
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
        Log.i(TAG, "addNode called - waiting for session to be ready")
        
        if (_isDisposed) {
            Log.w(TAG, "addNode called but wrapper is disposed, aborting")
            return
        }
        
        // Initial state check with error handling
        val initialEngineReady = try {
            sceneView.engine != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking initial engine state: ${e.message}", e)
            false
        }
        val initialModelLoaderReady = try {
            sceneView.modelLoader != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking initial modelLoader state: ${e.message}", e)
            false
        }
        Log.d(TAG, "Initial state - _isSessionReady: $_isSessionReady, engine: $initialEngineReady, modelLoader: $initialModelLoaderReady")
        
        // Wait for session to be ready before loading models
        // Check both the flag AND the actual engine/modelLoader availability
        var retries = 50  // Wait up to 5 seconds
        while (retries > 0 && !_isDisposed) {
            val engineReady = try {
                sceneView.engine != null
            } catch (e: Exception) {
                Log.w(TAG, "Exception checking engine in wait loop: ${e.message}")
                false
            }
            
            val modelLoaderReady = try {
                sceneView.modelLoader != null
            } catch (e: Exception) {
                Log.w(TAG, "Exception checking modelLoader in wait loop: ${e.message}")
                false
            }
            
            val sessionReady = _isSessionReady || (engineReady && modelLoaderReady)
            
            if (sessionReady && engineReady && modelLoaderReady) {
                Log.i(TAG, "Session is ready! _isSessionReady: $_isSessionReady, engine: true, modelLoader: true")
                break
            }
            
            try {
                delay(100)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Coroutine cancelled during wait loop")
                throw e // Re-throw to properly cancel
            }
            retries--
            if (retries % 10 == 0) {
                Log.d(TAG, "Still waiting for session to be ready... retries left: $retries, _isSessionReady: $_isSessionReady, engine: $engineReady, modelLoader: $modelLoaderReady")
            }
        }
        
        if (_isDisposed) {
            Log.w(TAG, "Wrapper disposed during wait, aborting node addition")
            return
        }
        
        // Final check - use engine and modelLoader as the source of truth
        if (_isDisposed) {
            Log.w(TAG, "Disposed before final engine/modelLoader check, aborting")
            return
        }
        
        val engineReady = try {
            sceneView.engine != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking engine: ${e.message}", e)
            false
        }
        
        val modelLoaderReady = try {
            sceneView.modelLoader != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking modelLoader: ${e.message}", e)
            false
        }
        
        if (!engineReady || !modelLoaderReady) {
            Log.e(TAG, "Session not ready after waiting 5 seconds - engine: $engineReady, modelLoader: $modelLoaderReady, _isSessionReady: $_isSessionReady")
            return
        }
        
        Log.d(TAG, "Session is ready, proceeding with node creation")
        
        Log.d(TAG, "Engine and modelLoader are ready, building node...")
        val node = buildNode(flutterNode) ?: run {
            Log.e(TAG, "Failed to build node - buildNode returned null")
            return
        }
        
        if (_isDisposed) {
            Log.w(TAG, "Wrapper disposed after building node, aborting node addition")
            return
        }
        
        Log.d(TAG, "Node built successfully, adding to scene...")
        try {
            if (_isDisposed) {
                Log.w(TAG, "Disposed right before adding node to scene, aborting")
                return
            }
            
            sceneView.addChildNode(node)
            
            if (_isDisposed) {
                Log.w(TAG, "Disposed right after adding node to scene")
                return
            }
            
            val nodeName = (flutterNode as? FlutterReferenceNode)?.name
            if (nodeName != null) {
                _nodesMap[nodeName] = node
                Log.i(TAG, "Node '$nodeName' successfully added to scene and stored in map")
            } else {
                Log.w(TAG, "Node added to scene but name is null, not stored in map")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding node to scene: ${e.message}", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Throwable adding node to scene: ${e.message}", e)
        }
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
        if (_isDisposed) {
            Log.w(TAG, "buildNode called but wrapper is disposed, aborting")
            return null
        }
        
        // Ensure engine and modelLoader are ready before loading models
        val engineReady = try {
            sceneView.engine != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking engine in buildNode: ${e.message}", e)
            false
        }
        
        val modelLoaderReady = try {
            sceneView.modelLoader != null
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking modelLoader in buildNode: ${e.message}", e)
            false
        }
        
        if (!engineReady || !modelLoaderReady) {
            Log.e(TAG, "Cannot load model: engine or modelLoader is not ready yet (engine: $engineReady, modelLoader: $modelLoaderReady)")
            return null
        }
        
        var model: ModelInstance? = null

        when (flutterNode) {
            is FlutterReferenceNode -> {
                val fileLocation = flutterNode.fileLocation
                val nodeName = flutterNode.name ?: "unnamed"
                
                // Check if it's a URL (starts with http:// or https://)
                val isUrl = fileLocation.startsWith("http://") || fileLocation.startsWith("https://")
                
                Log.i(TAG, "buildNode for '$nodeName': fileLocation=$fileLocation, isUrl=$isUrl, position=${flutterNode.position}, rotation=${flutterNode.rotation}, scaleUnits=${flutterNode.scaleUnits}")
                
                try {
                    // Double-check disposal before loading
                    if (_isDisposed) {
                        Log.w(TAG, "Disposed during model loading preparation, aborting")
                        return null
                    }
                    
                    val modelLoader = sceneView.modelLoader
                    if (modelLoader == null || _isDisposed) {
                        Log.w(TAG, "ModelLoader is null or disposed, cannot load model")
                        return null
                    }
                    
                    if (isUrl) {
                        // Load from URL directly
                        Log.i(TAG, "Loading model from URL: $fileLocation")
                        try {
                            if (!_isDisposed) {
                                // Check engine validity before loading
                                val engineValidBefore = try {
                                    sceneView.engine != null
                                } catch (e: Throwable) {
                                    false
                                }
                                if (!engineValidBefore || _isDisposed) {
                                    Log.w(TAG, "Engine invalid or disposed before model loading, aborting")
                                    return null
                                }
                                
                                model = modelLoader.loadModelInstance(fileLocation)
                                
                                // Check engine validity after loading (scene might have been destroyed during blocking call)
                                val engineValidAfter = try {
                                    sceneView.engine != null
                                } catch (e: Throwable) {
                                    false
                                }
                                
                                if (!engineValidAfter || _isDisposed) {
                                    Log.w(TAG, "Engine invalid or disposed after model loading, discarding model")
                                    model = null
                                } else if (model != null) {
                                    Log.i(TAG, "Model loaded successfully from URL")
                                } else {
                                    Log.e(TAG, "Model loading from URL returned null")
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Log.d(TAG, "Model loading cancelled: ${e.message}")
                            throw e // Re-throw to properly cancel
                        } catch (e: Throwable) {
                            // Handle any native exceptions that might occur if scene is destroyed during loading
                            if (_isDisposed) {
                                Log.w(TAG, "Exception during model loading, but wrapper is disposed: ${e.message}")
                                return null
                            } else {
                                throw e
                            }
                        }
                    } else {
                        // Load from Flutter asset
                        val assetKey = Utils.getFlutterAssetKey(activity, fileLocation)
                        Log.i(TAG, "Loading model from asset: $assetKey (original: $fileLocation)")
                        try {
                            if (!_isDisposed) {
                                // Check engine validity before loading
                                val engineValidBefore = try {
                                    sceneView.engine != null
                                } catch (e: Throwable) {
                                    false
                                }
                                if (!engineValidBefore || _isDisposed) {
                                    Log.w(TAG, "Engine invalid or disposed before model loading, aborting")
                                    return null
                                }
                                
                                model = modelLoader.loadModelInstance(assetKey)
                                
                                // Check engine validity after loading (scene might have been destroyed during blocking call)
                                val engineValidAfter = try {
                                    sceneView.engine != null
                                } catch (e: Throwable) {
                                    false
                                }
                                
                                if (!engineValidAfter || _isDisposed) {
                                    Log.w(TAG, "Engine invalid or disposed after model loading, discarding model")
                                    model = null
                                } else if (model != null) {
                                    Log.i(TAG, "Model loaded successfully from asset")
                                } else {
                                    Log.e(TAG, "Model loading from asset returned null")
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            Log.d(TAG, "Model loading cancelled: ${e.message}")
                            throw e // Re-throw to properly cancel
                        } catch (e: Throwable) {
                            // Handle any native exceptions that might occur if scene is destroyed during loading
                            if (_isDisposed) {
                                Log.w(TAG, "Exception during model loading, but wrapper is disposed: ${e.message}")
                                return null
                            } else {
                                throw e
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Model loading cancelled: ${e.message}")
                    throw e // Re-throw cancellation exceptions to properly cancel coroutine
                } catch (e: Exception) {
                    Log.e(TAG, "Exception loading model: ${e.message}", e)
                    e.printStackTrace()
                    return null
                } catch (e: Throwable) {
                    Log.e(TAG, "Throwable while loading model: ${e.message}", e)
                    e.printStackTrace()
                    return null
                }
            }
        }
        
        // Check disposal status again before creating ModelNode
        if (_isDisposed) {
            Log.w(TAG, "Disposed after model loading, aborting ModelNode creation")
            return null
        }
        
        if (model != null) {
            Log.d(TAG, "Creating ModelNode with scaleUnits=${flutterNode.scaleUnits}")
            val modelNode = try {
                ModelNode(modelInstance = model, scaleToUnits = flutterNode.scaleUnits).apply {
                    // Use worldPosition for absolute world-space positioning (not relative to camera)
                    // This ensures models stay fixed in world space as the user moves around
                    worldPosition = Position(flutterNode.position.x, flutterNode.position.y, flutterNode.position.z)
                    // Note: rotation in sceneview uses radians, but we're passing Float3 which might already be in radians
                    // For now, skip rotation setting if all values are zero
                    if (flutterNode.rotation.x != 0f || flutterNode.rotation.y != 0f || flutterNode.rotation.z != 0f) {
                        rotation = Rotation(
                            x = flutterNode.rotation.x,
                            y = flutterNode.rotation.y,
                            z = flutterNode.rotation.z
                        )
                    }
                    Log.i(TAG, "ModelNode created successfully - worldPosition: $worldPosition, rotation: $rotation, scale: $scale")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating ModelNode: ${e.message}", e)
                return null
            } catch (e: Throwable) {
                Log.e(TAG, "Throwable creating ModelNode: ${e.message}", e)
                return null
            }
            
            if (_isDisposed) {
                Log.w(TAG, "Disposed after ModelNode creation, aborting")
                return null
            }
            
            return modelNode
        } else {
            Log.e(TAG, "Model instance is null, cannot create ModelNode")
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