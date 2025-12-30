package io.github.sceneview.sceneview_flutter

import android.content.Context
import android.util.Log
import io.flutter.FlutterInjector


class Utils {
    companion object{
        fun getFlutterAssetKey(context:Context, flutterAsset: String): String {
            Log.d("Utils", flutterAsset)
            val loader = FlutterInjector.instance().flutterLoader()
            return loader.getLookupKeyForAsset(flutterAsset)
        }
    }
}