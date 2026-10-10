package com.pixelkaart.twinspace

import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder

/**
 * Runs in BOTH profiles. The instance living inside the work profile is the profile
 * owner, so that is where the actual clone/uninstall work happens. The main-profile UI
 * binds to this work-profile instance via DevicePolicyManager.bindDeviceAdminServiceAsUser.
 */
class CloneService : Service() {

    private val binder = object : ICloneService.Stub() {
        override fun cloneApp(packageName: String): Boolean {
            val dpm = getSystemService(DevicePolicyManager::class.java) ?: return false
            return try {
                dpm.installExistingPackage(admin(), packageName)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun uninstallApp(packageName: String): Boolean {
            return try {
                val installer = getSystemService(PackageInstaller::class.java)
                val intent = Intent(ACTION_UNINSTALL_DONE).setPackage(packageName)
                val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val pi = PendingIntent.getBroadcast(this@CloneService, packageName.hashCode(), intent, flags)
                installer.uninstall(packageName, pi.intentSender)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun admin(): ComponentName = ComponentName(applicationContext, AdminReceiver::class.java)

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val ACTION_UNINSTALL_DONE = "com.pixelkaart.twinspace.UNINSTALL_DONE"
    }
}
