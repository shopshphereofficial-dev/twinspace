package com.pixelkaart.twinspace

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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

        binding.toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            clonedMode = checkedId == R.id.btn_cloned
            adapter.clonedMode = clonedMode
            refresh()
        }
    }

    override fun onStart() {
        super.onStart()
        if (WorkProfileManager.isReady(this) && !bound) {
            WorkProfileManager.bind(this, conn)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            try {
                unbindService(conn)
            } catch (e: Exception) {
                // ignore
            }
            bound = false
            service = null
        }
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun updateState() {
        val ready = WorkProfileManager.isReady(this)
        binding.setupContainer.visibility = if (ready) View.GONE else View.VISIBLE
        binding.mainContainer.visibility = if (ready) View.VISIBLE else View.GONE
        if (ready) refresh()
    }

    private fun refresh() {
        val items = if (clonedMode) {
            WorkProfileManager.clonedApps(this)
        } else {
            WorkProfileManager.personalApps(this)
        }
        adapter.submit(items)
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.setText(if (clonedMode) R.string.empty_cloned else R.string.empty_personal)
    }

    private fun onAction(info: AppInfo, action: String) {
        when (action) {
            "open" -> WorkProfileManager.launch(this, info)
            "clone" -> {
                val svc = service
                val ok = svc != null && try {
                    svc.cloneApp(info.packageName)
                } catch (e: Exception) {
                    false
                }
                Toast.makeText(this, if (ok) R.string.cloned_toast else R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
            "uninstall" -> {
                val svc = service
                val ok = svc != null && try {
                    svc.uninstallApp(info.packageName)
                } catch (e: Exception) {
                    false
                }
                if (!ok) Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        }
        refresh()
    }
}
