package com.pixelkaart.twinspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/**
 * Cross-profile plumbing. TwinSpace is the profile owner of an isolated work profile.
 * Listing is done from the main profile (PackageManager / LauncherApps); actions that
 * must run as the profile owner are delegated to [CloneService] inside the work profile.
 */
object WorkProfileManager {

    const val PROVISION_REQUEST = 4001

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, AdminReceiver::class.java)

    fun workProfileUser(context: Context): UserHandle? {
        val um = context.getSystemService(UserManager::class.java) ?: return null
        val self = Process.myUserHandle()
        return um.userProfiles.firstOrNull { it != self }
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

    /** Bind to the work-profile instance of [CloneService]. */
    fun bind(context: Context, conn: ServiceConnection): Boolean {
        val user = workProfileUser(context) ?: return false
        val intent = Intent(context.applicationContext, CloneService::class.java)
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return try {
            dpm.bindDeviceAdminServiceAsUser(
                adminComponent(context), intent, conn, Context.BIND_AUTO_CREATE, user
            )
        } catch (e: Exception) {
            try {
                context.bindServiceAsUser(intent, conn, Context.BIND_AUTO_CREATE, user)
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun personalLaunchable(context: Context): List<AppInfo> {
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
    fun personalApps(context: Context): List<AppInfo> = personalLaunchable(context)

    /** Apps currently living inside the work profile (queried via LauncherApps). */
    fun clonedApps(context: Context): List<AppInfo> {
        val user = workProfileUser(context) ?: return emptyList()
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val infos = try {
            la.getActivityList(null, user)
        } catch (e: Exception) {
            return emptyList()
        }
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>()
        for (li in infos) {
            val pkg = li.componentName.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            out.add(
                AppInfo(
                    label = li.label?.toString() ?: pkg,
                    packageName = pkg,
                    icon = li.getIcon(0),
                    system = false,
                    component = li.componentName
                )
            )
        }
        out.sortBy { it.label.lowercase() }
        return out
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
