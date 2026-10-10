package com.pixelkaart.twinspace

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pixelkaart.twinspace.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private var clonedMode = false

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
        val ok = when (action) {
            "clone" -> WorkProfileManager.cloneApp(this, info.packageName)
            "uninstall" -> WorkProfileManager.uninstall(this, info.packageName)
            "freeze" -> WorkProfileManager.setFrozen(this, info.packageName, true)
            "unfreeze" -> WorkProfileManager.setFrozen(this, info.packageName, false)
            "open" -> WorkProfileManager.launch(this, info)
            else -> true
        }
        val msg = when {
            action == "clone" && ok -> R.string.cloned_toast
            action == "clone" -> R.string.action_failed
            !ok -> R.string.action_failed
            else -> 0
        }
        if (msg != 0) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refresh()
    }
}
