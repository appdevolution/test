package com.appdevolution.typewriter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : Activity() {
    companion object {
        private const val OPEN_TXT = 1001
        private const val EXPORT_TXT = 1002
        private const val FONT_MINION = 2001
        private const val FONT_COURIER = 2002
        private const val FONT_SPECIAL = 2003
        private const val FONT_GARAMOND = 2004
        private const val FONT_BASKERVILLE = 2005
        private const val FONT_MONO = 2006
        private const val PAPER_CREAM = 2101
        private const val PAPER_WHITE = 2102
        private const val PAPER_AGED = 2103
        private const val PAPER_DARK = 2104
    }

    private lateinit var shell: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var editorCard: FrameLayout
    private lateinit var titleEdit: EditText
    private lateinit var editor: EditText
    private lateinit var stats: TextView
    private lateinit var saveChip: TextView
    private lateinit var focusExit: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("typewriter_prefs", MODE_PRIVATE) }
    private val autosave by lazy { File(filesDir, "autosave.txt") }
    private var suppress = false
    private var beforeLength = 0
    private var focus = false
    private var bellArmed = true
    private var sounds = true
    private var haptics = true
    private var motion = true
    private var fontSize = 20f
    private var fontName = "Minion Pro"
    private var paperName = "Crema"

    private lateinit var pool: SoundPool
    private val keys = mutableListOf<Int>()
    private var sSpace = 0
    private var sReturn = 0
    private var sBack = 0
    private var sBell = 0

    private val doSave = Runnable { saveNow() }
    private val doStats = Runnable { updateStats(false) }
    private val fadeFocus = Runnable { if (focus) focusExit.animate().alpha(0.18f).setDuration(180).start() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        loadPrefs()
        initSounds()
        buildUi()
        restore()
        applyFont(fontName, false)
        applyPaper(paperName)
        updateStats(true)
    }

    override fun onPause() { super.onPause(); saveNow() }
    override fun onDestroy() { pool.release(); super.onDestroy() }

    private fun loadPrefs() {
        sounds = prefs.getBoolean("sounds", true)
        haptics = prefs.getBoolean("haptics", true)
        motion = prefs.getBoolean("visual", true)
        fontSize = prefs.getFloat("font_size", 20f)
        fontName = prefs.getString("font", "Minion Pro") ?: "Minion Pro"
        paperName = prefs.getString("paper", "Crema") ?: "Crema"
    }

    private fun initSounds() {
        val a = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(a).build()
        keys += pool.load(this, R.raw.key1, 1)
        keys += pool.load(this, R.raw.key2, 1)
        keys += pool.load(this, R.raw.key3, 1)
        sSpace = pool.load(this, R.raw.space, 1)
        sReturn = pool.load(this, R.raw.carriage_return, 1)
        sBack = pool.load(this, R.raw.backspace, 1)
        sBell = pool.load(this, R.raw.bell, 1)
    }

    private fun buildUi() {
        shell = FrameLayout(this).apply { setBackgroundColor(Color.rgb(234, 229, 220)) }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        shell.addView(page, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = shape(Color.rgb(38, 39, 42), 22f)
            elevation = dpf(3f)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "T"; gravity = Gravity.CENTER; textSize = 18f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(36, 36, 34))
            background = shape(Color.rgb(236, 219, 174), 15f)
        }
        titleRow.addView(brand, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
        titleEdit = EditText(this).apply {
            setText(prefs.getString("title", "Senza titolo")); hint = "Titolo del documento"
            setTextColor(Color.WHITE); setHintTextColor(Color.rgb(154,155,160)); setSingleLine(true)
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; background = null
        }
        titleRow.addView(titleEdit, LinearLayout.LayoutParams(0, dp(46), 1f))
        titleRow.addView(pill("•••", true) { showMainMenu(it) }, LinearLayout.LayoutParams(dp(48), dp(40)))
        topBar.addView(titleRow)

        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        actions.addView(pill("＋  Nuovo") { confirmNew() })
        actions.addView(pill("↗  Apri") { openTxt() })
        actions.addView(pill("⇩  Esporta") { exportTxt() })
        actions.addView(pill("Aa  Font") { showFontMenu(it) })
        actions.addView(pill("⛶  Focus") { toggleFocus() })
        scroll.addView(actions); topBar.addView(scroll); page.addView(topBar)

        editorCard = FrameLayout(this).apply {
            background = shape(Color.rgb(247, 239, 218), 24f); elevation = dpf(5f)
        }
        editor = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START; hint = "Inizia a scrivere…"
            setTextColor(Color.rgb(48,43,36)); setHintTextColor(Color.rgb(144,132,108))
            setTextSize(fontSize); setLineSpacing(dp(3).toFloat(), 1.18f)
            setPadding(dp(26), dp(24), dp(26), dp(72)); setBackgroundColor(Color.TRANSPARENT)
            isSingleLine = false; isVerticalScrollBarEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        editorCard.addView(editor, FrameLayout.LayoutParams(-1, -1))
        page.addView(editorCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), 0)
        }
        stats = TextView(this).apply { textSize = 12f; setTextColor(Color.rgb(83,78,70)) }
        bottom.addView(stats, LinearLayout.LayoutParams(0, dp(30), 1f))
        saveChip = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 11f; setPadding(dp(11),0,dp(11),0)
            setTextColor(Color.rgb(63,87,67)); background = shape(Color.rgb(217,231,216),14f)
        }
        bottom.addView(saveChip, LinearLayout.LayoutParams(-2, dp(28)))
        page.addView(bottom)

        focusExit = TextView(this).apply {
            text = "✕  Esci dal Focus"; gravity = Gravity.CENTER; textSize = 12f
            setTextColor(Color.WHITE); setPadding(dp(14),0,dp(14),0)
            background = shape(Color.argb(215,42,42,44),18f); visibility = View.GONE; alpha = 0.9f
            elevation = dpf(10f); setOnClickListener { toggleFocus() }
        }
        shell.addView(focusExit, FrameLayout.LayoutParams(-2, dp(38), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14); marginEnd = dp(14)
        })

        editor.setOnClickListener { if (focus) revealFocusExit() }
        editor.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { beforeLength = s?.length ?: 0 }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                feedback(s?.toString().orEmpty())
                saveChip.text = "Modificato"; saveChip.setTextColor(Color.rgb(117,87,47)); saveChip.background = shape(Color.rgb(241,227,201),14f)
                handler.removeCallbacks(doSave); handler.postDelayed(doSave, 420)
                handler.removeCallbacks(doStats); handler.postDelayed(doStats, 80)
            }
        })
        titleEdit.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { handler.removeCallbacks(doSave); handler.postDelayed(doSave, 420) }
        })
        setContentView(shell)
    }

    private fun pill(label: String, compact: Boolean = false, click: (View) -> Unit): TextView = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = if (compact) 15f else 12f
        setTextColor(Color.rgb(239,238,235)); setPadding(if (compact) dp(8) else dp(14),0,if (compact) dp(8) else dp(14),0)
        background = shape(Color.rgb(58,59,63),17f); isClickable = true; elevation = dpf(1f)
        setOnClickListener { v -> click(v) }
        if (!compact) layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(7) }
    }

    private fun shape(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dpf(radius)
    }

    private fun restore() {
        if (!autosave.exists()) return
        suppress = true
        try { editor.setText(autosave.readText()); editor.setSelection(editor.text.length) } finally { suppress = false }
    }

    private fun saveNow() {
        try {
            val tmp = File(filesDir, "autosave.tmp"); tmp.writeText(editor.text.toString())
            if (autosave.exists()) autosave.delete(); tmp.renameTo(autosave)
            prefs.edit().putString("title", titleEdit.text.toString().ifBlank { "Senza titolo" })
                .putString("font", fontName).putString("paper", paperName).putFloat("font_size", fontSize)
                .putBoolean("sounds", sounds).putBoolean("haptics", haptics).putBoolean("visual", motion).apply()
            updateStats(true)
        } catch (_: Exception) { saveChip.text = "Errore salvataggio" }
    }

    private fun updateStats(saved: Boolean) {
        val t = editor.text.toString(); val w = if (t.isBlank()) 0 else Regex("\\S+").findAll(t).count()
        stats.text = w.toString() + " parole   ·   " + t.length + " caratteri   ·   " + fontName
        if (saved) { saveChip.text = "✓ Salvato"; saveChip.setTextColor(Color.rgb(63,87,67)); saveChip.background = shape(Color.rgb(217,231,216),14f) }
    }

    private fun feedback(t: String) {
        if (t.length < beforeLength) { play(sBack,.78f); haptic(); move(-.8f); return }
        if (t.length <= beforeLength || t.isEmpty()) return
        when (t.last()) {
            '\n' -> { play(sReturn,.92f); bellArmed = true; haptic(); move(1.6f) }
            ' ' -> { play(sSpace,.70f); move(-.4f) }
            else -> { if (keys.isNotEmpty()) play(keys[Random.nextInt(keys.size)],.82f); haptic(); move(-.7f) }
        }
        val n = t.substringAfterLast('\n').length
        if (n >= 66 && bellArmed) { play(sBell,.72f); bellArmed = false }
        if (n < 55) bellArmed = true
    }

    private fun play(id: Int, vol: Float) { if (sounds && id != 0) pool.play(id,vol,vol,1,0,.97f + Random.nextFloat()*.06f) }
    private fun haptic() { if (haptics) editor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    private fun move(x: Float) { if (motion) { editor.animate().cancel(); editor.translationX = dpf(x); editor.animate().translationX(0f).setDuration(45).start() } }

    private fun confirmNew() {
        if (editor.text.isBlank()) { newDoc(); return }
        AlertDialog.Builder(this).setTitle("Nuovo documento")
            .setMessage("Il testo corrente è già salvato automaticamente. Creare un nuovo documento?")
            .setNegativeButton("Annulla",null).setPositiveButton("Crea") { _,_ -> newDoc() }.show()
    }
    private fun newDoc() { saveNow(); suppress = true; editor.setText(""); titleEdit.setText("Senza titolo"); suppress = false; saveNow(); editor.requestFocus() }

    private fun openTxt() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/*" }, OPEN_TXT)
    private fun exportTxt() {
        val n = titleEdit.text.toString().ifBlank { "manoscritto" }.replace(Regex("[^a-zA-Z0-9àèéìòùÀÈÉÌÒÙ _-]"),"").trim().ifBlank { "manoscritto" }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE,n + ".txt") }, EXPORT_TXT)
    }

    @Deprecated("Compatibilità Activity")
    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req,result,data); if (result != RESULT_OK) return; val uri = data?.data ?: return
        try {
            if (req == OPEN_TXT) {
                val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                saveNow(); suppress = true; editor.setText(txt); editor.setSelection(editor.text.length); titleEdit.setText(displayName(uri).substringBeforeLast('.')); suppress = false; saveNow()
                Toast.makeText(this,"Documento importato",Toast.LENGTH_SHORT).show()
            } else if (req == EXPORT_TXT) {
                contentResolver.openOutputStream(uri,"wt")?.use { it.write(editor.text.toString().toByteArray()) }
                Toast.makeText(this,"TXT esportato",Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { Toast.makeText(this,"Operazione non riuscita",Toast.LENGTH_LONG).show() }
    }

    private fun displayName(uri: android.net.Uri): String {
        var n = "Documento"; contentResolver.query(uri,null,null,null,null)?.use { c -> val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) n = c.getString(i) }; return n
    }

    private fun showFontMenu(v: View) {
        PopupMenu(this,v).apply {
            menu.add(Menu.NONE,FONT_MINION,0,"Minion Pro"); menu.add(Menu.NONE,FONT_COURIER,1,"Courier Prime")
            menu.add(Menu.NONE,FONT_SPECIAL,2,"Special Elite"); menu.add(Menu.NONE,FONT_GARAMOND,3,"EB Garamond")
            menu.add(Menu.NONE,FONT_BASKERVILLE,4,"Libre Baskerville"); menu.add(Menu.NONE,FONT_MONO,5,"Monospace Android")
            setOnMenuItemClickListener { m ->
                when(m.itemId) { FONT_MINION->applyFont("Minion Pro"); FONT_COURIER->applyFont("Courier Prime"); FONT_SPECIAL->applyFont("Special Elite"); FONT_GARAMOND->applyFont("EB Garamond"); FONT_BASKERVILLE->applyFont("Libre Baskerville"); FONT_MONO->applyFont("Monospace Android") }; true
            }; show()
        }
    }

    private fun applyFont(name: String, toast: Boolean = true) {
        try {
            editor.typeface = when(name) {
                "Minion Pro" -> Typeface.createFromAsset(assets,"fonts/MinionPro-Regular.otf")
                "Courier Prime" -> Typeface.createFromAsset(assets,"fonts/CourierPrime-Regular.ttf")
                "Special Elite" -> Typeface.createFromAsset(assets,"fonts/SpecialElite-Regular.ttf")
                "EB Garamond" -> Typeface.createFromAsset(assets,"fonts/EBGaramond-Regular.ttf")
                "Libre Baskerville" -> Typeface.createFromAsset(assets,"fonts/LibreBaskerville-Regular.ttf")
                else -> Typeface.MONOSPACE
            }
            fontName = name; prefs.edit().putString("font",fontName).apply(); updateStats(false)
            if (toast) Toast.makeText(this,name,Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            editor.typeface = Typeface.createFromAsset(assets,"fonts/CourierPrime-Regular.ttf"); fontName = "Courier Prime"; prefs.edit().putString("font",fontName).apply()
        }
    }

    private fun showMainMenu(v: View) {
        PopupMenu(this,v).apply {
            val p = menu.addSubMenu("Carta"); p.add(Menu.NONE,PAPER_CREAM,0,"Crema"); p.add(Menu.NONE,PAPER_WHITE,1,"Bianca"); p.add(Menu.NONE,PAPER_AGED,2,"Invecchiata"); p.add(Menu.NONE,PAPER_DARK,3,"Notturna")
            menu.add("Dimensione testo"); menu.add("Impostazioni"); menu.add("Salva ora"); menu.add("Come uscire dal Focus")
            setOnMenuItemClickListener { m ->
                when(m.itemId) { PAPER_CREAM->applyPaper("Crema"); PAPER_WHITE->applyPaper("Bianca"); PAPER_AGED->applyPaper("Invecchiata"); PAPER_DARK->applyPaper("Notturna")
                    else -> when(m.title.toString()) { "Dimensione testo"->fontSizeDialog(); "Impostazioni"->settingsDialog(); "Salva ora"->{saveNow();Toast.makeText(this@MainActivity,"Documento salvato",Toast.LENGTH_SHORT).show()}; "Come uscire dal Focus"->AlertDialog.Builder(this@MainActivity).setTitle("Uscire dal Focus").setMessage("Premi Indietro oppure tocca ‘Esci dal Focus’ in alto a destra. Se il pulsante è quasi trasparente, tocca il foglio.").setPositiveButton("OK",null).show() }
                }; true
            }; show()
        }
    }

    private fun applyPaper(n: String) {
        paperName = n
        when(n) {
            "Bianca" -> palette(Color.rgb(236,237,239),Color.rgb(253,253,252),Color.rgb(34,35,37),Color.rgb(134,136,140),Color.rgb(74,76,80),false)
            "Invecchiata" -> palette(Color.rgb(218,203,172),Color.rgb(235,218,177),Color.rgb(64,48,31),Color.rgb(129,104,74),Color.rgb(87,66,43),false)
            "Notturna" -> palette(Color.rgb(21,22,24),Color.rgb(31,32,35),Color.rgb(225,222,215),Color.rgb(126,127,132),Color.rgb(185,184,180),true)
            else -> palette(Color.rgb(234,229,220),Color.rgb(247,239,218),Color.rgb(48,43,36),Color.rgb(144,132,108),Color.rgb(83,78,70),false)
        }
        prefs.edit().putString("paper",paperName).apply()
    }

    private fun palette(outer:Int,paper:Int,text:Int,hint:Int,status:Int,dark:Boolean) {
        shell.setBackgroundColor(outer); editorCard.background = shape(paper,24f); editor.setTextColor(text); editor.setHintTextColor(hint); stats.setTextColor(status)
        window.statusBarColor = if(dark) Color.rgb(21,22,24) else Color.rgb(38,39,42); window.navigationBarColor = window.statusBarColor
    }

    private fun fontSizeDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(12),dp(24),0) }
        val value = TextView(this).apply { gravity = Gravity.CENTER; textSize = 18f; text = fontSize.roundToInt().toString() + " sp" }
        val seek = SeekBar(this).apply { max = 20; progress = (fontSize-14f).roundToInt().coerceIn(0,20); progressTintList = ColorStateList.valueOf(Color.rgb(88,75,55)) }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { fontSize = 14f+p; value.text = fontSize.roundToInt().toString()+" sp"; editor.setTextSize(fontSize) }
            override fun onStartTrackingTouch(s: SeekBar?)=Unit; override fun onStopTrackingTouch(s: SeekBar?)=Unit
        }); box.addView(value); box.addView(seek)
        AlertDialog.Builder(this).setTitle("Dimensione testo").setView(box).setPositiveButton("OK") { _,_ -> prefs.edit().putFloat("font_size",fontSize).apply() }.show()
    }

    private fun settingsDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(8),dp(24),0) }
        val a = CheckBox(this).apply { text="Suoni macchina da scrivere"; isChecked=sounds }
        val b = CheckBox(this).apply { text="Feedback aptico"; isChecked=haptics }
        val c = CheckBox(this).apply { text="Micro movimento del foglio"; isChecked=motion }
        box.addView(a);box.addView(b);box.addView(c)
        AlertDialog.Builder(this).setTitle("Impostazioni").setView(box).setNegativeButton("Annulla",null).setPositiveButton("Salva") { _,_ ->
            sounds=a.isChecked;haptics=b.isChecked;motion=c.isChecked;prefs.edit().putBoolean("sounds",sounds).putBoolean("haptics",haptics).putBoolean("visual",motion).apply()
        }.show()
    }

    private fun toggleFocus() {
        focus = !focus; topBar.visibility = if(focus) View.GONE else View.VISIBLE; stats.parent?.let { (it as View).visibility = if(focus) View.GONE else View.VISIBLE }
        page.setPadding(if(focus)0 else dp(12),if(focus)0 else dp(8),if(focus)0 else dp(12),if(focus)0 else dp(10))
        if(focus) {
            val c = when(paperName) { "Notturna"->Color.rgb(31,32,35);"Bianca"->Color.rgb(253,253,252);"Invecchiata"->Color.rgb(235,218,177);else->Color.rgb(247,239,218) }
            editorCard.background=shape(c,0f);focusExit.visibility=View.VISIBLE;focusExit.alpha=.9f;handler.removeCallbacks(fadeFocus);handler.postDelayed(fadeFocus,2600)
        } else { focusExit.visibility=View.GONE;handler.removeCallbacks(fadeFocus);applyPaper(paperName) }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if(focus) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_VISIBLE
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if(focus) Toast.makeText(this,"Focus · Indietro per uscire",Toast.LENGTH_SHORT).show()
    }

    private fun revealFocusExit() { if(focus) { focusExit.animate().cancel();focusExit.alpha=.92f;handler.removeCallbacks(fadeFocus);handler.postDelayed(fadeFocus,2200) } }

    @Deprecated("Compatibilità Activity")
    override fun onBackPressed() { if(focus) toggleFocus() else { saveNow(); super.onBackPressed() } }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()
    private fun dpf(v:Float)=v*resources.displayMetrics.density
}
