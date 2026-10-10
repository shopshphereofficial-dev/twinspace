package com.pixelkaart.twinspace

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

/**
 * All the cross-profile plumbing lives here. TwinSpace is the profile owner of an
 * isolated work profile; this object clones/uninstalls/freezes apps inside it.
 */
object WorkProfileManager {

    const val PROVISION_REQUEST = 4001
    const val ACTION_UNINSTALL_DONE = "com.pixelkaart.twinspace.UNINSTALL_DONE"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, AdminReceiver::class.java)

    fun workProfileUser(context: Context): UserHandle? {
        val um = context.getSystemService(UserManager::class.java) ?: return null
        return um.userProfiles.firstOrNull { um.isManagedProfile(it) }
    }

    fun isReady(context: Context): Boolean = workProfileUser(context) != null

    /** Kick off the system flow that creates the managed work profile. */
    fun provision(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent(context)
            )
        }
        if (context is Activity) {
            context.startActivityForResult(intent, PROVISION_REQUEST)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun workContext(context: Context): Context? {
        val user = workProfileUser(context) ?: return null
        return context.createContextAsUser(user, 0)
    }

    private fun workDpm(context: Context): DevicePolicyManager? =
        workContext(context)?.getSystemService(DevicePolicyManager::class.java)

    private fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>()
        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val ai = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: Exception) {
                continue
            }
            out.add(
                AppInfo(
                    label = ai.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ai.loadIcon(pm),
                    system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    component = ComponentName(pkg, ri.activityInfo.name)
                )
            )
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    /** Apps installed in the main (personal) profile. */
    fun personalApps(context: Context): List<AppInfo> = launchable(context)

    /** Apps currently installed inside the work profile. */
    fun clonedApps(context: Context): List<AppInfo> {
        val workCtx = workContext(context) ?: return emptyList()
        val dpm = workDpm(context)
        val admin = adminComponent(context)
        return launchable(workCtx).map { info ->
            val hidden = try {
                dpm?.isApplicationHidden(admin, info.packageName) ?: false
            } catch (e: Exception) {
                false
            }
            info.copy(frozen = hidden)
        }
    }

    /** Install an existing personal app into the work profile (the actual "clone"). */
    fun cloneApp(context: Context, packageName: String): Boolean {
        val dpm = workDpm(context) ?: return false
        return try {
            dpm.installExistingPackage(adminComponent(context), packageName)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Remove an app from the work profile only. */
    fun uninstall(context: Context, packageName: String): Boolean {
        val workCtx = workContext(context) ?: return false
        return try {
            val installer = workCtx.getSystemService(PackageInstaller::class.java)
            val intent = Intent(ACTION_UNINSTALL_DONE).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(context, packageName.hashCode(), intent, flags)
            installer.uninstall(packageName, pi.intentSender)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Freeze (hide) or unfreeze an app inside the work profile. */
    fun setFrozen(context: Context, packageName: String, frozen: Boolean): Boolean {
        val dpm = workDpm(context) ?: return false
        return try {
            dpm.setApplicationHidden(adminComponent(context), packageName, frozen)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Launch an app that lives in the work profile. */
    fun launch(context: Context, info: AppInfo): Boolean {
        val user = workProfileUser(context) ?: return false
        val la = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            la.startMainActivity(info.component, user, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
