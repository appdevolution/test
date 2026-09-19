package com.appdevolution.typewriter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Typewriter V1
 *
 * Editor Android nativo in Kotlin.
 * Nessuna WebView: l'area di scrittura è un vero EditText Android.
 *
 * Funzioni principali:
 * - foglio color carta e temi alternativi
 * - autosalvataggio locale
 * - import / export TXT tramite Storage Access Framework
 * - suoni meccanici a bassa latenza con SoundPool
 * - feedback aptico
 * - modalità focus a pieno schermo
 * - contatore parole e caratteri
 * - font incorporati dedicati alla scrittura
 * - import opzionale di Minion Pro da file OTF/TTF posseduto dall'utente
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_OPEN_TXT = 1001
        private const val REQ_EXPORT_TXT = 1002
        private const val REQ_IMPORT_MINION = 1003

        private const val MENU_FONT_COURIER = 2001
        private const val MENU_FONT_SPECIAL = 2002
        private const val MENU_FONT_GARAMOND = 2003
        private const val MENU_FONT_BASKERVILLE = 2004
        private const val MENU_FONT_MONO = 2005
        private const val MENU_FONT_MINION = 2006
        private const val MENU_IMPORT_MINION = 2007

        private const val MENU_PAPER_CREAM = 2101
        private const val MENU_PAPER_WHITE = 2102
        private const val MENU_PAPER_AGED = 2103
        private const val MENU_PAPER_DARK = 2104
    }

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var titleEdit: EditText
    private lateinit var editor: EditText
    private lateinit var status: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("typewriter_prefs", MODE_PRIVATE) }
    private val autosaveFile by lazy { File(filesDir, "autosave.txt") }
    private val minionFile by lazy { File(filesDir, "minion-pro.otf") }

    private var suppressWatcher = false
    private var beforeLength = 0
    private var focusMode = false
    private var bellArmed = true

    private var soundsEnabled = true
    private var hapticsEnabled = true
    private var visualEffectsEnabled = true
    private var fontSizeSp = 20f
    private var currentFont = "Courier Prime"
    private var currentPaper = "Crema"

    private lateinit var soundPool: SoundPool
    private val keySounds = mutableListOf<Int>()
    private var soundSpace = 0
    private var soundReturn = 0
    private var soundBackspace = 0
    private var soundBell = 0

    private val saveRunnable = Runnable { saveNow() }
    private val statsRunnable = Runnable { updateStats(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.rgb(52, 44, 35)
        window.navigationBarColor = Color.rgb(52, 44, 35)

        loadPreferences()
        initSoundPool()
        buildUi()
        restoreAutosave()
        applyFont(currentFont, showToast = false)
        applyPaper(currentPaper)
        updateStats(true)
    }

    override fun onPause() {
        super.onPause()
        saveNow()
    }

    override fun onDestroy() {
        soundPool.release()
        super.onDestroy()
    }

    /** Carica le preferenze che devono sopravvivere alla chiusura dell'app. */
    private fun loadPreferences() {
        soundsEnabled = prefs.getBoolean("sounds", true)
        hapticsEnabled = prefs.getBoolean("haptics", true)
        visualEffectsEnabled = prefs.getBoolean("visual", true)
        fontSizeSp = prefs.getFloat("font_size", 20f)
        currentFont = prefs.getString("font", "Courier Prime") ?: "Courier Prime"
        currentPaper = prefs.getString("paper", "Crema") ?: "Crema"
    }

    /**
     * SoundPool tiene in memoria i piccoli campioni audio ed è adatto agli effetti
     * rapidissimi come i colpi dei tasti della macchina da scrivere.
     */
    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        keySounds += soundPool.load(this, R.raw.key1, 1)
        keySounds += soundPool.load(this, R.raw.key2, 1)
        keySounds += soundPool.load(this, R.raw.key3, 1)
        soundSpace = soundPool.load(this, R.raw.space, 1)
        soundReturn = soundPool.load(this, R.raw.carriage_return, 1)
        soundBackspace = soundPool.load(this, R.raw.backspace, 1)
        soundBell = soundPool.load(this, R.raw.bell, 1)
    }

    /** Crea l'interfaccia completamente con componenti Android nativi. */
    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 234, 210))
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(54, 46, 37))
        }

        titleEdit = EditText(this).apply {
            setText(prefs.getString("title", "Senza titolo"))
            setTextColor(Color.WHITE)
            hint = "Titolo"
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
            textSize = 16f
            background = null
            setPadding(dp(8), 0, dp(8), 0)
        }

        val titleParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        toolbar.addView(titleEdit, titleParams)

        toolbar.addView(makeToolbarButton("Nuovo") { confirmNewDocument() })
        toolbar.addView(makeToolbarButton("Apri") { openTxt() })
        toolbar.addView(makeToolbarButton("Esporta") { exportTxt() })
        toolbar.addView(makeToolbarButton("Aa") { showFontMenu(it) })
        toolbar.addView(makeToolbarButton("Focus") { toggleFocus() })
        toolbar.addView(makeToolbarButton("⋮") { showMainMenu(it) })

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(toolbar, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll)

        editor = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.rgb(45, 40, 34))
            setHintTextColor(Color.rgb(135, 122, 100))
            hint = "Inizia a scrivere…"
            setTextSize(fontSizeSp)
            setLineSpacing(dp(3).toFloat(), 1.16f)
            setPadding(dp(32), dp(28), dp(32), dp(72))
            setBackgroundColor(Color.TRANSPARENT)
            isSingleLine = false
            isVerticalScrollBarEnabled = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                beforeLength = s?.length ?: 0
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (suppressWatcher) return

                val text = s?.toString().orEmpty()
                handleKeystrokeFeedback(text)

                handler.removeCallbacks(saveRunnable)
                handler.postDelayed(saveRunnable, 450)

                handler.removeCallbacks(statsRunnable)
                handler.postDelayed(statsRunnable, 90)
            }
        })

        root.addView(
            editor,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        status = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundColor(Color.rgb(54, 46, 37))
            setTextColor(Color.rgb(234, 224, 205))
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        titleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(saveRunnable)
                handler.postDelayed(saveRunnable, 450)
            }
        })

        setContentView(root)
    }

    private fun makeToolbarButton(label: String, action: (View) -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 11f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(9), 0, dp(9), 0)
            setOnClickListener(action)
        }
    }

    /** Ripristina il testo locale salvato automaticamente nell'ultima sessione. */
    private fun restoreAutosave() {
        if (!autosaveFile.exists()) return
        suppressWatcher = true
        try {
            editor.setText(autosaveFile.readText(Charsets.UTF_8))
            editor.setSelection(editor.text.length)
        } finally {
            suppressWatcher = false
        }
    }

    /** Salvataggio atomico semplice su storage privato dell'app. */
    private fun saveNow() {
        try {
            val tmp = File(filesDir, "autosave.tmp")
            tmp.writeText(editor.text.toString(), Charsets.UTF_8)
            if (autosaveFile.exists()) autosaveFile.delete()
            tmp.renameTo(autosaveFile)

            prefs.edit()
                .putString("title", titleEdit.text.toString().ifBlank { "Senza titolo" })
                .putString("font", currentFont)
                .putString("paper", currentPaper)
                .putFloat("font_size", fontSizeSp)
                .putBoolean("sounds", soundsEnabled)
                .putBoolean("haptics", hapticsEnabled)
                .putBoolean("visual", visualEffectsEnabled)
                .apply()

            updateStats(true)
        } catch (e: Exception) {
            status.text = "Errore salvataggio: " + e.localizedMessage
        }
    }

    private fun updateStats(saved: Boolean) {
        val text = editor.text.toString()
        val words = if (text.isBlank()) 0 else Regex("\\S+").findAll(text).count()
        val chars = text.length
        val prefix = if (saved) "Salvato" else "Scrittura"
        status.text = prefix + "  •  " + words + " parole  •  " + chars +
            " caratteri  •  " + currentFont
    }

    /**
     * Produce feedback diverso per lettera, spazio, invio e cancellazione.
     * Vicino alla colonna 68 viene riprodotta la campanella classica.
     */
    private fun handleKeystrokeFeedback(text: String) {
        if (text.length < beforeLength) {
            play(soundBackspace, 0.78f)
            haptic()
            tinyPaperMovement(-1f)
            return
        }

        if (text.length <= beforeLength || text.isEmpty()) return

        val last = text.last()
        when (last) {
            '\n' -> {
                play(soundReturn, 0.92f)
                bellArmed = true
                haptic()
                tinyPaperMovement(2f)
            }
            ' ' -> {
                play(soundSpace, 0.72f)
                tinyPaperMovement(-0.6f)
            }
            else -> {
                if (keySounds.isNotEmpty()) {
                    play(keySounds[Random.nextInt(keySounds.size)], 0.84f)
                }
                haptic()
                tinyPaperMovement(-1f)
            }
        }

        val lineLength = text.substringAfterLast('\n').length
        if (lineLength >= 66 && bellArmed) {
            play(soundBell, 0.72f)
            bellArmed = false
        }
        if (lineLength < 55) bellArmed = true
    }

    private fun play(id: Int, volume: Float) {
        if (!soundsEnabled || id == 0) return
        val rate = 0.97f + Random.nextFloat() * 0.06f
        soundPool.play(id, volume, volume, 1, 0, rate)
    }

    private fun haptic() {
        if (hapticsEnabled) {
            editor.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun tinyPaperMovement(amountDp: Float) {
        if (!visualEffectsEnabled) return
        editor.animate().cancel()
        editor.translationX = dpFloat(amountDp)
        editor.animate().translationX(0f).setDuration(45).start()
    }

    private fun confirmNewDocument() {
        if (editor.text.isBlank()) {
            newDocument()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Nuovo documento")
            .setMessage("Il testo corrente è già salvato automaticamente. Creare un nuovo documento vuoto?")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Crea") { _, _ -> newDocument() }
            .show()
    }

    private fun newDocument() {
        saveNow()
        suppressWatcher = true
        editor.setText("")
        titleEdit.setText("Senza titolo")
        suppressWatcher = false
        saveNow()
        editor.requestFocus()
    }

    /** Apre un TXT scelto dall'utente senza richiedere accesso generale ai file. */
    private fun openTxt() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, REQ_OPEN_TXT)
    }

    /** Esporta il testo usando lo Storage Access Framework di Android. */
    private fun exportTxt() {
        val safeTitle = titleEdit.text.toString()
            .ifBlank { "manoscritto" }
            .replace(Regex("[^a-zA-Z0-9àèéìòùÀÈÉÌÒÙ _-]"), "")
            .trim()
            .ifBlank { "manoscritto" }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, safeTitle + ".txt")
        }
        startActivityForResult(intent, REQ_EXPORT_TXT)
    }

    /**
     * Consente di utilizzare Minion Pro posseduto dall'utente senza includere
     * copie non autorizzate del font nella distribuzione pubblica.
     */
    private fun importMinionPro() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_IMPORT_MINION)
    }

    @Deprecated("Compatibilità con Activity nativa senza dipendenze AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_OPEN_TXT -> {
                try {
                    val loaded = contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: return

                    saveNow()
                    suppressWatcher = true
                    editor.setText(loaded)
                    editor.setSelection(editor.text.length)
                    titleEdit.setText(queryDisplayName(uri).substringBeforeLast('.'))
                    suppressWatcher = false
                    saveNow()
                    Toast.makeText(this, "Documento importato", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Impossibile aprire il file", Toast.LENGTH_LONG).show()
                }
            }

            REQ_EXPORT_TXT -> {
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(editor.text.toString().toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "TXT esportato", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Errore durante l'esportazione", Toast.LENGTH_LONG).show()
                }
            }

            REQ_IMPORT_MINION -> {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(minionFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val test = Typeface.createFromFile(minionFile)
                    editor.typeface = test
                    currentFont = "Minion Pro"
                    prefs.edit().putString("font", currentFont).apply()
                    updateStats(true)
                    Toast.makeText(this, "Minion Pro importato", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    minionFile.delete()
                    Toast.makeText(
                        this,
                        "Il file selezionato non sembra essere un font valido.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String {
        var name = "Documento"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
        }
        return name
    }

    private fun showFontMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_FONT_COURIER, 0, "Courier Prime")
        popup.menu.add(Menu.NONE, MENU_FONT_SPECIAL, 1, "Special Elite")
        popup.menu.add(Menu.NONE, MENU_FONT_GARAMOND, 2, "EB Garamond")
        popup.menu.add(Menu.NONE, MENU_FONT_BASKERVILLE, 3, "Libre Baskerville")
        popup.menu.add(Menu.NONE, MENU_FONT_MONO, 4, "Monospace Android")

        if (minionFile.exists()) {
            popup.menu.add(Menu.NONE, MENU_FONT_MINION, 5, "Minion Pro")
        }
        popup.menu.add(Menu.NONE, MENU_IMPORT_MINION, 6, "Importa Minion Pro…")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_FONT_COURIER -> applyFont("Courier Prime")
                MENU_FONT_SPECIAL -> applyFont("Special Elite")
                MENU_FONT_GARAMOND -> applyFont("EB Garamond")
                MENU_FONT_BASKERVILLE -> applyFont("Libre Baskerville")
                MENU_FONT_MONO -> applyFont("Monospace Android")
                MENU_FONT_MINION -> applyFont("Minion Pro")
                MENU_IMPORT_MINION -> importMinionPro()
            }
            true
        }
        popup.show()
    }

    private fun applyFont(name: String, showToast: Boolean = true) {
        try {
            editor.typeface = when (name) {
                "Courier Prime" -> Typeface.createFromAsset(assets, "fonts/CourierPrime-Regular.ttf")
                "Special Elite" -> Typeface.createFromAsset(assets, "fonts/SpecialElite-Regular.ttf")
                "EB Garamond" -> Typeface.createFromAsset(assets, "fonts/EBGaramond-Regular.ttf")
                "Libre Baskerville" -> Typeface.createFromAsset(
                    assets,
                    "fonts/LibreBaskerville-Regular.ttf"
                )
                "Minion Pro" -> {
                    if (minionFile.exists()) Typeface.createFromFile(minionFile)
                    else {
                        importMinionPro()
                        return
                    }
                }
                else -> Typeface.MONOSPACE
            }
            currentFont = name
            prefs.edit().putString("font", currentFont).apply()
            updateStats(false)
            if (showToast) Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            editor.typeface = Typeface.MONOSPACE
            currentFont = "Monospace Android"
        }
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        val paper = popup.menu.addSubMenu("Carta")
        paper.add(Menu.NONE, MENU_PAPER_CREAM, 0, "Crema")
        paper.add(Menu.NONE, MENU_PAPER_WHITE, 1, "Bianca")
        paper.add(Menu.NONE, MENU_PAPER_AGED, 2, "Invecchiata")
        paper.add(Menu.NONE, MENU_PAPER_DARK, 3, "Notturna")

        popup.menu.add("Dimensione testo")
        popup.menu.add("Impostazioni")
        popup.menu.add("Salva ora")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PAPER_CREAM -> applyPaper("Crema")
                MENU_PAPER_WHITE -> applyPaper("Bianca")
                MENU_PAPER_AGED -> applyPaper("Invecchiata")
                MENU_PAPER_DARK -> applyPaper("Notturna")
                else -> when (item.title.toString()) {
                    "Dimensione testo" -> showFontSizeDialog()
                    "Impostazioni" -> showSettingsDialog()
                    "Salva ora" -> {
                        saveNow()
                        Toast.makeText(this, "Documento salvato", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }

        popup.show()
    }

    private fun applyPaper(name: String) {
        currentPaper = name

        when (name) {
            "Bianca" -> {
                root.setBackgroundColor(Color.rgb(250, 249, 246))
                editor.setTextColor(Color.rgb(36, 35, 33))
                editor.setHintTextColor(Color.rgb(135, 130, 120))
            }
            "Invecchiata" -> {
                root.setBackgroundColor(Color.rgb(224, 204, 160))
                editor.setTextColor(Color.rgb(55, 42, 28))
                editor.setHintTextColor(Color.rgb(125, 103, 76))
            }
            "Notturna" -> {
                root.setBackgroundColor(Color.rgb(31, 30, 28))
                editor.setTextColor(Color.rgb(225, 218, 202))
                editor.setHintTextColor(Color.rgb(128, 124, 116))
            }
            else -> {
                root.setBackgroundColor(Color.rgb(244, 234, 210))
                editor.setTextColor(Color.rgb(45, 40, 34))
                editor.setHintTextColor(Color.rgb(135, 122, 100))
            }
        }

        prefs.edit().putString("paper", currentPaper).apply()
    }

    private fun showFontSizeDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }

        val value = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            text = fontSizeSp.roundToInt().toString() + " sp"
        }

        val seek = SeekBar(this).apply {
            max = 20
            progress = (fontSizeSp - 14f).roundToInt().coerceIn(0, 20)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                fontSizeSp = 14f + progress
                value.text = fontSizeSp.roundToInt().toString() + " sp"
                editor.setTextSize(fontSizeSp)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        container.addView(value)
        container.addView(seek)

        AlertDialog.Builder(this)
            .setTitle("Dimensione testo")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putFloat("font_size", fontSizeSp).apply()
            }
            .show()
    }

    private fun showSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }

        val cbSound = CheckBox(this).apply {
            text = "Suoni macchina da scrivere"
            isChecked = soundsEnabled
        }
        val cbHaptic = CheckBox(this).apply {
            text = "Feedback aptico"
            isChecked = hapticsEnabled
        }
        val cbVisual = CheckBox(this).apply {
            text = "Micro movimento del foglio"
            isChecked = visualEffectsEnabled
        }

        box.addView(cbSound)
        box.addView(cbHaptic)
        box.addView(cbVisual)

        AlertDialog.Builder(this)
            .setTitle("Impostazioni")
            .setView(box)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva") { _, _ ->
                soundsEnabled = cbSound.isChecked
                hapticsEnabled = cbHaptic.isChecked
                visualEffectsEnabled = cbVisual.isChecked

                prefs.edit()
                    .putBoolean("sounds", soundsEnabled)
                    .putBoolean("haptics", hapticsEnabled)
                    .putBoolean("visual", visualEffectsEnabled)
                    .apply()
            }
            .show()
    }

    /**
     * Modalità immersiva: nasconde toolbar, statistiche e barre di sistema.
     * Un tasto Focus resta disponibile quando si esce dalla modalità tramite Back.
     */
    private fun toggleFocus() {
        focusMode = !focusMode
        toolbar.visibility = if (focusMode) View.GONE else View.VISIBLE
        status.visibility = if (focusMode) View.GONE else View.VISIBLE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (focusMode) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }

        if (focusMode) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            Toast.makeText(this, "Modalità Focus", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Compatibilità con Activity nativa")
    override fun onBackPressed() {
        if (focusMode) {
            toggleFocus()
        } else {
            saveNow()
            super.onBackPressed()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun dpFloat(value: Float): Float =
        value * resources.displayMetrics.density
}
