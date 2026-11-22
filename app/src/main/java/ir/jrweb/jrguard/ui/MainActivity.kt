package ir.jrweb.jrguard.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ir.jrweb.jrguard.R
import ir.jrweb.jrguard.data.GuardStorage
import ir.jrweb.jrguard.data.UnlockSession

class MainActivity : AppCompatActivity() {

    private lateinit var rvSessions: RecyclerView
    private lateinit var tvSummary: TextView

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val msg = if (granted) "دسترسی دوربین فعال شد" else "دسترسی دوربین داده نشد"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvSessions = findViewById(R.id.rvSessions)
        tvSummary = findViewById(R.id.tvSummary)
        rvSessions.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener {
            ensurePermissions()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            refreshList()
        }

        findViewById<Button>(R.id.btnHostConfig).setOnClickListener {
            showHostDialog()
        }

        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun ensurePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(
                this,
                "لطفاً دسترسی Usage Access را برای جی‌آرجان فعال کنید",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshList() {
        val sessions = GuardStorage.loadSessions(this)
        val totalVisits = sessions.sumOf { it.visits.size }
        tvSummary.text = "تعداد باز شدن قفل: ${sessions.size} | ثبت بازدید اپ‌ها: $totalVisits"
        rvSessions.adapter = SessionsAdapter(sessions) { session ->
            showColleagueTagDialog(session)
        }
    }

    private fun showColleagueTagDialog(session: UnlockSession) {
        val input = EditText(this).apply {
            setText(session.colleagueTag)
            hint = "نام همکار یا فرد (مثلاً نوید، داوود یا خودم)"
        }
        AlertDialog.Builder(this)
            .setTitle("ثبت فردِ بازکننده گوشی")
            .setMessage("زمان: ${session.formattedTime}")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val tag = input.text.toString().trim().ifBlank { "نامشخص" }
                GuardStorage.updateColleagueTag(this, session.id, tag)
                refreshList()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showHostDialog() {
        val urlInput = EditText(this).apply {
            setText(GuardStorage.getHostUrl(this@MainActivity))
            hint = "آدرس ingest.php روی هاست (اختیاری)"
        }
        AlertDialog.Builder(this)
            .setTitle("تنظیمات ارسال به هاست")
            .setView(urlInput)
            .setPositiveButton("ذخیره") { _, _ ->
                GuardStorage.setHostConfig(
                    this,
                    urlInput.text.toString(),
                    GuardStorage.getApiToken(this)
                )
                Toast.makeText(this, "آدرس هاست ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
}

private class SessionsAdapter(
    private val items: List<UnlockSession>,
    private val onTagClick: (UnlockSession) -> Unit
) : RecyclerView.Adapter<SessionsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvSessionTime)
        val tvTag: TextView = view.findViewById(R.id.tvColleagueTag)
        val tvApps: TextView = view.findViewById(R.id.tvVisitedApps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.tvTime.text = s.formattedTime
        holder.tvTag.text = "فرد: ${s.colleagueTag}"
        holder.tvTag.setOnClickListener { onTagClick(s) }

        holder.tvApps.text = if (s.visits.isEmpty()) {
            "برنامه‌ای در بازه پایش ثبت نشده است"
        } else {
            s.visits.joinToString("\n") { v ->
                "• ${v.appLabel} (${v.durationSec} ثانیه)"
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
