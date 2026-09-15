package com.oracle.dalvik

/**
 * JNI entry used by PojavLauncher's libpojavexec.so to start the embedded JRE.
 */
object VMLauncher {
    @JvmStatic external fun launchJVM(args: Array<String>): Int
}
