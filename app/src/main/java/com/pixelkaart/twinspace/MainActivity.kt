package com.pixelkaart.twinspace

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelkaart.twinspace.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private var clonedMode = false
    private var service: ICloneService? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ICloneService.Stub.asInterface(binder)
            bound = true
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter(mutableListOf(), clonedMode) { info, action -> onAction(info, action) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.setupButton.setOnClickListener { WorkProfileManager.provision(this) }
        binding.foreignSettingsButton.setOnClickListener { openSystemSettings() }

        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> { refresh(); true }
                R.id.action_remove -> { openSystemSettings(); true }
                else -> false
            }
        }

        binding.toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            clonedMode = checkedId == R.id.btn_cloned
            adapter.clonedMode = clonedMode
            refresh()
        }
    }

    override fun onStart() {
        super.onStart()
        if (WorkProfileManager.isOurProfile(this) && !bound) {
            WorkProfileManager.bind(this, conn)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            try { unbindService(conn) } catch (e: Exception) { /* ignore */ }
            bound = false
            service = null
        }
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        val user = WorkProfileManager.managedProfile(this)
        val ours = user != null && WorkProfileManager.isOurProfile(this)
        binding.setupContainer.visibility = if (user == null) View.VISIBLE else View.GONE
        binding.foreignContainer.visibility = if (user != null && !ours) View.VISIBLE else View.GONE
        binding.mainContainer.visibility = if (ours) View.VISIBLE else View.GONE
        if (ours) {
            if (!bound) WorkProfileManager.bind(this, conn)
            refresh()
        }
    }

    private fun refresh() {
        if (binding.mainContainer.visibility != View.VISIBLE) return
        if (clonedMode) {
            val svc = service
            val pkgs = if (svc != null) try { svc.listPackages() } catch (e: Exception) { null } else null
            val frozen: ((String) -> Boolean)? =
                if (svc != null) ({ p -> try { svc.isFrozen(p) } catch (e: Exception) { false } }) else null
            adapter.submit(WorkProfileManager.clonedApps(this, pkgs, frozen))
        } else {
            adapter.submit(WorkProfileManager.personalApps(this))
        }
        val empty = adapter.itemCount == 0
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.emptyView.setText(if (clonedMode) R.string.empty_cloned else R.string.empty_personal)
    }

    private fun onAction(info: AppInfo, action: String) {
        val svc = service
        when (action) {
            "open" -> WorkProfileManager.launch(this, info)
            "clone" -> {
                val status = if (svc != null) try { svc.cloneApp(info.packageName) } catch (e: Exception) { "error: ${e.message}" } else "service not connected"
                toast(if (status == "ok") getString(R.string.cloned_toast) else status)
            }
            "uninstall" -> {
                val status = if (svc != null) try { svc.uninstallApp(info.packageName) } catch (e: Exception) { "error: ${e.message}" } else "service not connected"
                if (status != "ok") toast(status)
            }
            "freeze" -> try { svc?.setFrozen(info.packageName, true) } catch (e: Exception) { /* ignore */ }
            "unfreeze" -> try { svc?.setFrozen(info.packageName, false) } catch (e: Exception) { /* ignore */ }
        }
        refresh()
    }

    private fun openSystemSettings() {
        toast(getString(R.string.remove_hint))
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { /* ignore */ }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
