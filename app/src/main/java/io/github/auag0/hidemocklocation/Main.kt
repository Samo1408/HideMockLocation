package io.github.auag0.hidemocklocation

import android.annotation.SuppressLint
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class Main : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        hookLocationMethods(param.classLoader)
        if (param.packageName != "android") {
            hookSettingsMethods(param.classLoader)
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun hookLocationMethods(classLoader: ClassLoader) {
        val locationClass = classLoader.loadClass("android.location.Location")

        // Hooked android.location.Location isFromMockProvider()
        hookAllMethods(locationClass, "isFromMockProvider") { _ -> false }
        // Hooked android.location.Location isMock()
        hookAllMethods(locationClass, "isMock") { _ -> false }
        // Hooked android.location.Location setIsFromMockProvider()
        hookAllMethods(locationClass, "setIsFromMockProvider") { chain ->
            val args = chain.args.toTypedArray()
            val isFromMockProvider = args[0] as Boolean?
            if (isFromMockProvider == true) {
                args[0] = false
            }
            chain.proceed(args)
        }
        // Hooked android.location.Location setMock()
        hookAllMethods(locationClass, "setMock") { chain ->
            val args = chain.args.toTypedArray()
            val mock = args[0] as Boolean?
            if (mock == true) {
                args[0] = false
            }
            chain.proceed(args)
        }
        // Hooked android.location.Location getExtras()
        hookAllMethods(locationClass, "getExtras") { chain ->
            var extras: Bundle? = chain.proceed() as Bundle?
            extras = getPatchedBundle(extras)
            return@hookAllMethods extras
        }
        // Hooked android.location.Location setExtras()
        hookAllMethods(locationClass, "setExtras") { chain ->
            val args = chain.args.toTypedArray()
            val extras = args[0] as Bundle?
            args[0] = getPatchedBundle(extras)
            chain.proceed(args)
        }
        // Hooked android.location.Location set()
        hookAllMethods(locationClass, "set") { chain ->
            val hasMockProviderMaskField = locationClass.getDeclaredField("HAS_MOCK_PROVIDER_MASK").apply { isAccessible = true }
            val hasMockProviderMask = hasMockProviderMaskField.getInt(null)

            val mFieldsMaskField = locationClass.getDeclaredField("mFieldsMask").apply { isAccessible = true }
            var mFieldsMask = mFieldsMaskField.getInt(chain.thisObject)
            mFieldsMask = mFieldsMask and hasMockProviderMask.inv()
            mFieldsMaskField.setInt(chain.thisObject, mFieldsMask)

            val mExtrasField = locationClass.getDeclaredField("mExtras").apply { isAccessible = true }
            var mExtras = mExtrasField.get(chain.thisObject) as? Bundle?

            mExtras = getPatchedBundle(mExtras)
            mExtrasField.set(chain.thisObject, mExtras)
        }
    }

    private fun hookSettingsMethods(classLoader: ClassLoader) {
        // Hooked android.provider.Settings.* getStringForUser()
        val settingsClassNames = arrayOf(
            "android.provider.Settings.Secure",
            "android.provider.Settings.System",
            "android.provider.Settings.Global",
            "android.provider.Settings.NameValueCache"
        )
        settingsClassNames.forEach {
            val clazz = classLoader.loadClass(it)
            hookAllMethods(clazz, "getStringForUser") { chain ->
                val args = chain.args.toTypedArray()
                val name: String? = args[1] as? String?
                return@hookAllMethods when (name) {
                    "mock_location" -> "0"
                    else -> chain.proceed()
                }
            }
        }
    }

    /**
     * if "mockLocation" containsKey in the given bundle, set it to false
     *
     * @param origBundle original Bundle object
     * @return Bundle with "mockLocation" set to false
     */
    private fun getPatchedBundle(origBundle: Bundle?): Bundle? {
        if (origBundle?.containsKey("mockLocation") == true) {
            origBundle.putBoolean("mockLocation", false)
        }
        return origBundle
    }

    private fun hookAllMethods(clazz: Class<*>, methodName: String, hooker: XposedInterface.Hooker) {
        clazz.declaredMethods
            .filter { it.name.equals(methodName) }
            .forEach { method -> hook(method).intercept(hooker) }
    }
}