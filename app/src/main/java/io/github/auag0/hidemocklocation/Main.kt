package io.github.auag0.hidemocklocation

import android.annotation.SuppressLint
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class Main : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        hookLocationMethods(param.classLoader)
        hookSettingsMethods(param.classLoader)
    }

    @SuppressLint("SoonBlockedPrivateApi", "BlockedPrivateApi")
    private fun hookLocationMethods(classLoader: ClassLoader) {
        val locationClass = classLoader.loadClass("android.location.Location")

        hookAllMethods(locationClass, "isFromMockProvider") { _ -> false }
        hookAllMethods(locationClass, "isMock") { _ -> false }

        hookAllMethods(locationClass, "setIsFromMockProvider") { chain ->
            val args = chain.args.toTypedArray()
            args[0] = false
            chain.proceed(args)
        }

        hookAllMethods(locationClass, "setMock") { chain ->
            val args = chain.args.toTypedArray()
            args[0] = false
            chain.proceed(args)
        }

        hookAllMethods(locationClass, "getExtras") { chain ->
            var extras: Bundle? = chain.proceed() as Bundle?
            extras = getPatchedBundle(extras)
            return@hookAllMethods extras
        }

        hookAllMethods(locationClass, "setExtras") { chain ->
            val args = chain.args.toTypedArray()
            val extras = args[0] as Bundle?
            args[0] = getPatchedBundle(extras)
            chain.proceed(args)
        }

        val hasMockProviderMaskField = runCatching {
            locationClass.getDeclaredField("HAS_MOCK_PROVIDER_MASK").apply { isAccessible = true }
        }.getOrNull()
        val mFieldsMaskField = runCatching {
            locationClass.getDeclaredField("mFieldsMask").apply { isAccessible = true }
        }.getOrNull()
        val mExtrasField = runCatching {
            locationClass.getDeclaredField("mExtras").apply { isAccessible = true }
        }.getOrNull()

        hookAllMethods(locationClass, "set") { chain ->
            chain.proceed()

            if (hasMockProviderMaskField != null && mFieldsMaskField != null) {
                val hasMockProviderMask = hasMockProviderMaskField.getInt(null)

                var mFieldsMask = mFieldsMaskField.getInt(chain.thisObject)
                mFieldsMask = mFieldsMask and hasMockProviderMask.inv()
                mFieldsMaskField.setInt(chain.thisObject, mFieldsMask)
            }

            if (mExtrasField != null) {
                var mExtras = mExtrasField.get(chain.thisObject) as? Bundle?
                mExtras = getPatchedBundle(mExtras)
                mExtrasField.set(chain.thisObject, mExtras)
            }
        }
    }

    private fun hookSettingsMethods(classLoader: ClassLoader) {
        val clazz = classLoader.loadClass("android.provider.Settings.Secure")
        hookAllMethods(clazz, "getStringForUser") { chain ->
            val args = chain.args.toTypedArray()
            val name: String? = args[1] as? String?
            if (name == "mock_location") {
                return@hookAllMethods "0"
            }
            return@hookAllMethods chain.proceed()
        }
    }

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