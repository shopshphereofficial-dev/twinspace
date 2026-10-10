package com.pixelkaart.twinspace

import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder

/**
 * Runs in BOTH profiles. The instance inside the work profile is the profile owner,
 * so that is where clone/uninstall/freeze happen. The main-profile UI binds to it via
 * DevicePolicyManager.bindDeviceAdminServiceAsUser.
 */
class CloneService : Service() {

    private val dpm get() = getSystemService(DevicePolicyManager::class.java)
    private fun admin() = ComponentName(applicationContext, AdminReceiver::class.java)

    private val binder = object : ICloneService.Stub() {

        override fun isOwner(): Boolean =
            try { dpm?.isProfileOwnerApp(packageName) ?: false } catch (e: Exception) { false }

        override fun cloneApp(packageName: String): String {
            val d = dpm ?: return "no DevicePolicyManager"
            return try {
                d.installExistingPackage(admin(), packageName)
                "ok"
            } catch (e: Exception) {
                "clone failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }

        override fun uninstallApp(packageName: String): String {
            return try {
                val installer = getSystemService(PackageInstaller::class.java)
                val intent = Intent(ACTION_UNINSTALL_DONE).setPackage(packageName)
                val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val pi = PendingIntent.getBroadcast(this@CloneService, packageName.hashCode(), intent, flags)
                installer.uninstall(packageName, pi.intentSender)
                "ok"
            } catch (e: Exception) {
                "uninstall failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }

        override fun setFrozen(packageName: String, frozen: Boolean): Boolean =
            try { dpm?.setApplicationHidden(admin(), packageName, frozen); true } catch (e: Exception) { false }

        override fun isFrozen(packageName: String): Boolean =
            try { dpm?.isApplicationHidden(admin(), packageName) ?: false } catch (e: Exception) { false }

        override fun listPackages(): List<String> {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
            val d = dpm
            val a = admin()
            val out = ArrayList<String>()
            for (ai in pm.getInstalledApplications(0)) {
                val p = ai.packageName
                if (p == packageName) continue
                val hidden = try { d?.isApplicationHidden(a, p) ?: false } catch (e: Exception) { false }
                if (p in launchable || hidden) out.add(p)
            }
            return out
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val ACTION_UNINSTALL_DONE = "com.pixelkaart.twinspace.UNINSTALL_DONE"
    }
}
