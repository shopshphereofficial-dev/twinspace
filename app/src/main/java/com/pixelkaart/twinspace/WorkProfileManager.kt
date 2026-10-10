package com.pixelkaart.twinspace

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat

object WorkProfileManager {

    const val PROVISION_REQUEST = 4001

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, AdminReceiver::class.java)

    /** The managed profile associated with this user, if any. */
    fun managedProfile(context: Context): UserHandle? {
        val um = context.getSystemService(UserManager::class.java) ?: return null
        val self = Process.myUserHandle()
        return um.userProfiles.firstOrNull { it != self }
    }

    /** True only if OUR app lives in that profile (i.e. we created it). */
    fun isOurProfile(context: Context): Boolean {
        val user = managedProfile(context) ?: return false
        val la = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            la.getApplicationInfo(context.packageName, 0, user)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isReady(context: Context): Boolean = isOurProfile(context)

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

    fun bind(context: Context, conn: ServiceConnection): Boolean {
        val user = managedProfile(context) ?: return false
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
            val ai = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { continue }
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

    fun personalApps(context: Context): List<AppInfo> = personalLaunchable(context)

    private fun componentFor(la: LauncherApps, user: UserHandle, pkg: String): ComponentName? =
        try { la.getActivityList(pkg, user).firstOrNull()?.componentName } catch (e: Exception) { null }

    /**
     * Apps living in the work profile. When [packages] is provided (from the bound
     * service, which runs inside the profile and can therefore also see frozen apps)
     * we resolve each one; otherwise we fall back to launcher-visible activities.
     */
    fun clonedApps(
        context: Context,
        packages: List<String>?,
        frozenLookup: ((String) -> Boolean)? = null
    ): List<AppInfo> {
        val user = managedProfile(context) ?: return emptyList()
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val pm = context.packageManager
        val fallbackIcon: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher)!!
        val out = ArrayList<AppInfo>()

        if (packages == null) {
            val infos = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            val seen = HashSet<String>()
            for (li in infos) {
                val pkg = li.componentName.packageName
                if (pkg == context.packageName) continue
                if (!seen.add(pkg)) continue
                out.add(AppInfo(li.label?.toString() ?: pkg, pkg, li.getIcon(0), false, li.componentName))
            }
        } else {
            for (pkg in packages) {
                if (pkg == context.packageName) continue
                val ai = try { la.getApplicationInfo(pkg, 0, user) } catch (e: Exception) { null }
                val label = ai?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() } ?: pkg
                val icon = ai?.let { runCatching { it.loadIcon(pm) }.getOrNull() } ?: fallbackIcon
                val comp = componentFor(la, user, pkg) ?: ComponentName(pkg, "")
                out.add(AppInfo(label, pkg, icon, false, comp, frozenLookup?.invoke(pkg) ?: false))
            }
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    fun launch(context: Context, info: AppInfo): Boolean {
        val user = managedProfile(context) ?: return false
        val la = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            la.startMainActivity(info.component, user, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
