package com.ybapps.offlineapkbackup

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val apps = mutableListOf<ApplicationInfo>()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private var selected: ApplicationInfo? = null
    private var pendingApp: ApplicationInfo? = null
    private val createBackup = 4101
    private val chooseBackup = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 20, 35)
        window.navigationBarColor = Color.rgb(15, 20, 35)
        buildUi()
        loadApps()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 12)
            setBackgroundColor(Color.rgb(15, 20, 35))
        }
        root.addView(TextView(this).apply {
            text = "מחלץ ומגבה APK"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
        })
        root.addView(TextView(this).apply {
            text = "גיבוי מקומי • בלי אינטרנט • yb apps"
            textSize = 13f
            setTextColor(Color.rgb(135, 180, 255))
            gravity = Gravity.RIGHT
            setPadding(0, 6, 0, 16)
        })
        val search = EditText(this).apply {
            hint = "חיפוש אפליקציה..."
            textSize = 15f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(31, 40, 60))
            setPadding(12, 8, 12, 8)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply {
            text = "בחר אפליקציה מהרשימה כדי לגבות אותה."
            textSize = 13f
            setTextColor(Color.rgb(180, 195, 220))
            gravity = Gravity.RIGHT
            setPadding(0, 12, 0, 8)
        }
        root.addView(status)
        list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.rgb(42, 53, 76))
            dividerHeight = 1
            setBackgroundColor(Color.rgb(20, 27, 43))
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(makeButton("גיבוי APK + נתונים (Root אם זמין)").apply {
            setOnClickListener { selected?.let { beginBackup(it) } ?: say("בחר אפליקציה תחילה") }
        })
        root.addView(makeButton("פתיחת קובץ גיבוי / התקנה מחדש").apply {
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(i, chooseBackup)
            }
        })
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { showApps(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        setContentView(root)
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43, 102, 220)))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
    }

    private fun loadApps() {
        apps.clear()
        apps.addAll(packageManager.getInstalledApplications(0).filter {
            it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM == 0)
        }.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() })
        showApps("")
        status.text = "נמצאו ${apps.size} אפליקציות משתמש. הגיבוי נשמר מקומית."
    }

    private fun showApps(query: String) {
        val filtered = apps.filter {
            packageManager.getApplicationLabel(it).toString().contains(query, true) || it.packageName.contains(query, true)
        }
        list.adapter = object : ArrayAdapter<ApplicationInfo>(this, android.R.layout.simple_list_item_1, filtered) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                    setPadding(14, 13, 14, 13)
                    textSize = 15f
                    setTextColor(Color.WHITE)
                }
                val app = getItem(position)!!
                tv.text = "${packageManager.getApplicationLabel(app)}\n${app.packageName}"
                return tv
            }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            selected = filtered[position]
            status.text = "נבחרה: ${packageManager.getApplicationLabel(selected!!)}"
        }
    }

    private fun beginBackup(app: ApplicationInfo) {
        pendingApp = app
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, app.packageName.replace('.', '_') + ".obak")
        }
        startActivityForResult(intent, createBackup)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            createBackup -> pendingApp?.let { exportBackup(it, data.data!!) }
            chooseBackup -> importBackup(data.data!!)
        }
    }

    private fun exportBackup(app: ApplicationInfo, uri: Uri) {
        status.text = "יוצר גיבוי..."
        Thread {
            var dataIncluded = false
            var rootMessage = "לא נמצאה הרשאת Root"
            val temp = File(cacheDir, "app-data-${System.currentTimeMillis()}.tar.gz")
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "tar -czf '${temp.absolutePath}' -C /data/user/0 '${app.packageName}'"))
                val exit = p.waitFor()
                if (exit == 0 && temp.exists() && temp.length() > 0) {
                    dataIncluded = true
                    rootMessage = "נתונים פרטיים נוספו לגיבוי (Root)"
                } else rootMessage = "הנתונים הפרטיים לא נגישים; יגובה APK בלבד"
            } catch (_: Exception) { rootMessage = "ללא Root זמין: יגובה APK בלבד" }
            try {
                contentResolver.openOutputStream(uri)?.use { raw ->
                    ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                        zip.putNextEntry(ZipEntry("base.apk"))
                        File(app.sourceDir).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        val version = try { packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "unknown" }
                        val meta = "package=${app.packageName}\nlabel=${packageManager.getApplicationLabel(app)}\nversion=$version\nprivate_data_included=$dataIncluded\n"
                        zip.putNextEntry(ZipEntry("backup-info.txt"))
                        zip.write(meta.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        if (dataIncluded) {
                            zip.putNextEntry(ZipEntry("data.tar.gz"))
                            temp.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: throw IOException("לא ניתן לכתוב לתיקייה שנבחרה")
                runOnUiThread { status.text = "הגיבוי נשמר. $rootMessage"; Toast.makeText(this, "הגיבוי הסתיים", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { status.text = "שגיאה בשמירת הגיבוי: ${e.message}" }
            } finally { temp.delete() }
        }.start()
    }

    private fun importBackup(uri: Uri) {
        Thread {
            val apkOut = File(cacheDir, "restore-${System.currentTimeMillis()}.apk")
            try {
                var found = false
                contentResolver.openInputStream(uri).use { input ->
                    java.util.zip.ZipInputStream(BufferedInputStream(input!!)).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == "base.apk") {
                                FileOutputStream(apkOut).use { zip.copyTo(it) }
                                found = true
                                break
                            }
                        }
                    }
                }
                runOnUiThread {
                    if (found) {
                        status.text = "ה-APK חולץ. אשר התקנה במסך הבא. שחזור נתונים פרטיים עדיין דורש שלב Root ייעודי."
                        val install = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse("file://" + apkOut.absolutePath), "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(install) } catch (e: Exception) { status.text = "לא ניתן לפתוח התקנה: ${e.message}" }
                    } else status.text = "הקובץ לא מכיל base.apk"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "לא ניתן לקרוא את קובץ הגיבוי: ${e.message}" }
            }
        }.start()
    }

    private fun say(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
