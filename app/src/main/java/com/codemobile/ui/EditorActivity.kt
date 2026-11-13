package com.codemobile.ui

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codemobile.R
import com.codemobile.editor.EditorPaneView
import com.codemobile.exec.ExecutionDispatcher
import com.codemobile.files.FileRepository
import com.codemobile.files.OpenFile
import com.codemobile.files.PermissionHelper
import java.io.File

/**
 * Activity única de la app (MVP): explorador (pantalla inicial) + tabbar +
 * editor + consola + barra de controles. El inset del teclado se maneja a
 * mano (ver setupKeyboardInsetHandling), así que la barra de controles queda
 * empujada arriba de él de forma confiable en cualquier versión de Android.
 */
class EditorActivity : AppCompatActivity() {

    private val openFiles = mutableListOf<OpenFile>()
    private var activeIndex = 0
    private var currentDir: File = FileRepository.rootDir()
    private var isLandscapeForced = false

    private lateinit var tabRecycler: RecyclerView
    private lateinit var tabAdapter: TabBarAdapter
    private lateinit var pathBar: TextView
    private lateinit var explorerContainer: FrameLayout
    private lateinit var explorerRecycler: RecyclerView
    private lateinit var explorerAdapter: FileExplorerAdapter
    private lateinit var editorContainer: FrameLayout
    private lateinit var consoleContainer: FrameLayout
    private lateinit var controlBar: ControlBarView
    private lateinit var tabBarRow: LinearLayout
    private lateinit var runButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var folderButton: TextView

    private var consoleOutputView: TextView? = null
    private var isKeyboardCurrentlyOpen: Boolean = false
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Se maneja el inset del teclado (IME) manualmente en vez de confiar en
        // android:windowSoftInputMode="adjustResize": en Android 11+ con un tema
        // moderno, adjustResize por sí solo puede no redimensionar el layout de
        // forma confiable. Con setDecorFitsSystemWindows(false) + un listener de
        // insets, empujamos nosotros mismos el contenido (y por lo tanto la barra
        // de controles) para que quede siempre arriba del teclado real.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_editor)

        bindViews()
        setupTabs()
        setupExplorer()
        setupControlButtons()
        setupKeyboardInsetHandling()
        setupKeyboardVisibilityWatcher()
        setupBackNavigation()

        if (!PermissionHelper.hasStorageAccess()) {
            showPermissionDialog()
        }

        // La pantalla inicial es el explorador de archivos (ver activity_editor.xml:
        // explorerContainer empieza visible, editorContainer/tabBarRow/controlBar
        // ocultos). No se abre ninguna pestaña "sin título" automáticamente.
        refreshExplorer()
    }

    private fun bindViews() {
        tabRecycler = findViewById(R.id.tabRecycler)
        pathBar = findViewById(R.id.pathBar)
        explorerContainer = findViewById(R.id.explorerContainer)
        explorerRecycler = findViewById(R.id.explorerRecycler)
        editorContainer = findViewById(R.id.editorContainer)
        consoleContainer = findViewById(R.id.consoleContainer)
        controlBar = findViewById(R.id.controlBar)
        tabBarRow = findViewById(R.id.tabBarRow)
        runButton = findViewById(R.id.runButton)
        saveButton = findViewById(R.id.saveButton)
        folderButton = findViewById(R.id.folderButton)
    }

    // ---------- Pestañas ----------

    private fun setupTabs() {
        tabAdapter = TabBarAdapter(
            files = openFiles,
            onTabClick = { index -> switchToTab(index) },
            onAddClick = { showNewFileDialog() }
        )
        tabRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        tabRecycler.adapter = tabAdapter
    }

    private fun addNewTab(openFile: OpenFile) {
        openFiles.add(openFile)
        activeIndex = openFiles.size - 1
        tabAdapter.setActiveIndex(activeIndex)
        tabAdapter.notifyDataSetChanged()
        renderActiveEditor()
        showEditorMode()
    }

    /** Pasa de la pantalla de explorador a la de editor (tabbar + ruta + editor + controles). */
    private fun showEditorMode() {
        explorerContainer.visibility = View.GONE
        tabBarRow.visibility = View.VISIBLE
        pathBar.visibility = View.VISIBLE
        editorContainer.visibility = View.VISIBLE
        controlBar.visibility = View.VISIBLE
        applyHeaderVisibility(isKeyboardCurrentlyOpen)
        updateBackCallbackState()
    }

    /** Vuelve a la pantalla de explorador (no cierra las pestañas, solo las oculta). */
    private fun showExplorerMode() {
        persistCurrentFile()
        editorContainer.visibility = View.GONE
        tabBarRow.visibility = View.GONE
        pathBar.visibility = View.GONE
        controlBar.visibility = View.GONE
        explorerContainer.visibility = View.VISIBLE
        refreshExplorer()
        updateBackCallbackState()
    }

    private fun switchToTab(index: Int) {
        if (index !in openFiles.indices) return
        persistCurrentFile()
        activeIndex = index
        tabAdapter.setActiveIndex(activeIndex)
        renderActiveEditor()
    }

    private fun saveCurrentEditorStateIntoModel() {
        val pane = editorContainer.getChildAt(0) as? EditorPaneView ?: return
        val of = openFiles.getOrNull(activeIndex) ?: return
        of.content = StringBuilder(pane.codeEditText.text?.toString() ?: "")
        of.cursorPosition = pane.codeEditText.selectionStart
    }

    /** Vuelca el editor activo al modelo y lo escribe a disco (si ya tiene un File asociado). */
    private fun persistCurrentFile() {
        saveCurrentEditorStateIntoModel()
        val of = openFiles.getOrNull(activeIndex) ?: return
        if (of.file != null) {
            FileRepository.save(of)
        }
    }

    private fun renderActiveEditor() {
        val of = openFiles.getOrNull(activeIndex) ?: return
        editorContainer.removeAllViews()

        val pane = EditorPaneView(this)
        pane.codeEditText.lang = FileRepository.langFor(of.extension)
        pane.codeEditText.openFile = of
        pane.codeEditText.setText(of.content.toString())
        pane.codeEditText.setSelection(of.cursorPosition.coerceIn(0, of.content.length))
        pane.codeEditText.onPendingCloserChanged = { closer, count ->
            controlBar.updatePendingCloser(closer, count)
        }
        editorContainer.addView(pane)

        pane.codeEditText.selectModeActive = controlBar.selectModeActive
        controlBar.target = pane.codeEditText
        controlBar.updatePendingCloser(of.peekCloser(), of.pendingCount())

        pathBar.text = of.file?.absolutePath ?: "(sin guardar) ${of.displayName}.${of.extension}"
    }

    // ---------- Nuevo archivo (nombre + color) ----------

    private fun showNewFileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_file, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.editFileName)
        val colorGrid = dialogView.findViewById<GridLayout>(R.id.colorGrid)

        var selectedColor = 0
        val swatchSize = (36 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        val cellWidth = swatchSize + margin * 2

        // Ancho real disponible dentro del diálogo (ancho de pantalla menos el
        // padding de 20dp por lado que tiene dialog_new_file.xml), para que las
        // columnas calculadas nunca empujen círculos fuera del margen visible.
        val dialogPadding = (20 * resources.displayMetrics.density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - dialogPadding * 2
        val columns = (availableWidth / cellWidth).coerceAtLeast(1)
        colorGrid.columnCount = columns
        colorGrid.rowCount = (paletteSize() + columns - 1) / columns

        val swatches = mutableListOf<View>()
        for (i in 0 until paletteSize()) {
            val swatch = View(this)
            val lp = GridLayout.LayoutParams()
            lp.width = swatchSize
            lp.height = swatchSize
            lp.setMargins(margin, margin, margin, margin)
            swatch.layoutParams = lp

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(resources.getColor(paletteColorRes(i), theme))
            if (i == selectedColor) {
                drawable.setStroke((2 * resources.displayMetrics.density).toInt(), resources.getColor(R.color.text_primary, theme))
            }
            swatch.background = drawable

            swatch.setOnClickListener {
                selectedColor = i
                for ((idx, s) in swatches.withIndex()) {
                    val d = s.background as GradientDrawable
                    if (idx == i) {
                        d.setStroke((2 * resources.displayMetrics.density).toInt(), resources.getColor(R.color.text_primary, theme))
                    } else {
                        d.setStroke(0, 0)
                    }
                }
            }
            swatches.add(swatch)
            colorGrid.addView(swatch)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_new_file)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isNotEmpty()) {
                    createAndOpenFile(name, selectedColor)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createAndOpenFile(name: String, colorIndex: Int) {
        val file = FileRepository.createFile(currentDir, name)
        val openFile = OpenFile.fromFile(file, colorIndex)
        addNewTab(openFile)
    }

    // ---------- Explorador ----------

    private fun setupExplorer() {
        explorerAdapter = FileExplorerAdapter(emptyList()) { file -> onExplorerItemClick(file) }
        explorerRecycler.layoutManager = LinearLayoutManager(this)
        explorerRecycler.adapter = explorerAdapter

        // Botón 📁 de la tabbar: vuelve de editor a explorador (solo visible
        // cuando ya hay archivos abiertos, porque la tabbar está oculta si no).
        folderButton.setOnClickListener { showExplorerMode() }

        // Botón grande de la pantalla de explorador: crea el primer/siguiente archivo.
        findViewById<TextView>(R.id.createFileButtonBig).setOnClickListener { showNewFileDialog() }
    }

    private fun refreshExplorer() {
        explorerAdapter.update(FileRepository.list(currentDir))
    }

    /** El back del sistema solo navega dentro del explorador: sube una carpeta
     *  si no estamos en la raíz. No cambia entre explorador/editor (para eso
     *  ya está el botón 📁) y no cierra pestañas. En la raíz, o fuera del
     *  explorador, se deja pasar el back normal (sale de la app). */
    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                currentDir = currentDir.parentFile ?: currentDir
                refreshExplorer()
                updateBackCallbackState()
            }
        }
        backCallback = callback
        onBackPressedDispatcher.addCallback(this, callback)
        updateBackCallbackState()
    }

    private fun updateBackCallbackState() {
        val atRoot = currentDir.absolutePath == FileRepository.rootDir().absolutePath
        val inExplorer = explorerContainer.visibility == View.VISIBLE
        backCallback?.isEnabled = inExplorer && !atRoot
    }

    private fun onExplorerItemClick(file: File) {
        if (file.isDirectory) {
            currentDir = file
            refreshExplorer()
            updateBackCallbackState()
        } else {
            val openFile = OpenFile.fromFile(file, colorIndex = openFiles.size % paletteSize())
            addNewTab(openFile)
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_needed_title)
            .setMessage(R.string.permission_needed_body)
            .setPositiveButton(R.string.permission_continue) { _, _ ->
                PermissionHelper.requestStorageAccess(this)
            }
            .setCancelable(false)
            .show()
    }

    // ---------- Botones: Run, carpeta (tabbar) y rotar (barra de controles) ----------

    private fun setupControlButtons() {
        runButton.setOnClickListener { runCurrentFile() }
        saveButton.setOnClickListener { persistCurrentFile() }
        controlBar.onRotateClick = { toggleLandscape() }
    }

    override fun onPause() {
        super.onPause()
        // Guarda a disco si el usuario sale de la app (cambia de app, apaga la
        // pantalla, etc.) para no perder cambios sin guardar explícitamente.
        if (editorContainer.visibility == View.VISIBLE) {
            persistCurrentFile()
        }
    }

    private fun toggleLandscape() {
        isLandscapeForced = !isLandscapeForced
        requestedOrientation = if (isLandscapeForced) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun runCurrentFile() {
        persistCurrentFile()
        val of = openFiles.getOrNull(activeIndex) ?: return
        val lang = FileRepository.langFor(of.extension)
        val code = of.content.toString()

        showConsole()
        appendConsole("▶ Ejecutando ${of.displayName}.${of.extension}…\n")

        ExecutionDispatcher.run(this, code, lang) { result ->
            if (result.output.isNotEmpty()) appendConsole(result.output)
            if (result.error != null) appendConsole("\n⚠ ${result.error}\n")
            appendConsole("— listo en ${result.durationMs}ms —\n")
        }
    }

    private fun showConsole() {
        if (consoleContainer.childCount == 0) {
            val view = layoutInflater.inflate(R.layout.view_console, consoleContainer, false)
            consoleOutputView = view.findViewById(R.id.consoleOutput)
            view.findViewById<TextView>(R.id.consoleClear).setOnClickListener {
                consoleOutputView?.text = ""
            }
            view.findViewById<TextView>(R.id.consoleClose).setOnClickListener {
                consoleContainer.visibility = View.GONE
            }
            consoleContainer.addView(view)
        }
        consoleContainer.visibility = View.VISIBLE
    }

    private fun appendConsole(text: String) {
        consoleOutputView?.append(text)
    }

    // ---------- Teclado: empujar la barra de controles arriba de él, y
    // ocultar el header en landscape mientras está visible ----------

    /**
     * Escucha el inset real del IME (teclado) y aplica ese espacio como padding
     * inferior del layout raíz, para que todo lo que esté antes de controlBar en
     * el árbol de vistas (incluyéndola a ella) quede empujado arriba del teclado.
     * Esto reemplaza la dependencia de windowSoftInputMode=adjustResize, que en
     * Android 11+ con setDecorFitsSystemWindows(false) no redimensiona el layout
     * por sí solo.
     */
    private fun setupKeyboardInsetHandling() {
        val rootView = findViewById<View>(R.id.rootLayout)
        var wasKeyboardOpen = false

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboardOpen = imeInsets.bottom > systemBarInsets.bottom
            isKeyboardCurrentlyOpen = keyboardOpen

            // Top: con setDecorFitsSystemWindows(false) el sistema deja de
            // reservar el espacio de la barra de estado/notch, así que hay que
            // compensarlo a mano o el header queda dibujado detrás de ella.
            // Bottom = el espacio que ocupa el teclado (o la barra de
            // navegación cuando el teclado está cerrado, para no quedar tapado
            // por los botones de sistema).
            val bottomPadding = maxOf(imeInsets.bottom, systemBarInsets.bottom)
            view.setPadding(view.paddingLeft, systemBarInsets.top, view.paddingRight, bottomPadding)

            if (keyboardOpen != wasKeyboardOpen) {
                wasKeyboardOpen = keyboardOpen
                applyHeaderVisibility(keyboardOpen)
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    /** Se mantiene por compatibilidad de nombre; la lógica real vive en setupKeyboardInsetHandling. */
    private fun setupKeyboardVisibilityWatcher() {
        // intencionalmente vacío: ver setupKeyboardInsetHandling()
    }

    private fun applyHeaderVisibility(keyboardOpen: Boolean) {
        // No forzar nada si estamos en modo explorador: ahí tabBarRow/pathBar
        // deben seguir ocultas sin importar el teclado.
        if (editorContainer.visibility != View.VISIBLE) return
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // Guard explícito: el header solo puede ocultarse en el único caso
        // deseado (landscape + teclado visible). Cualquier otra combinación
        // (incluyendo portrait, sea o no el teclado visible) lo deja VISIBLE
        // sin excepción, para que un cálculo erróneo de inset nunca lo oculte.
        val hideHeader = isLandscape && keyboardOpen
        if (!isLandscape) {
            tabBarRow.visibility = View.VISIBLE
            pathBar.visibility = View.VISIBLE
            return
        }
        tabBarRow.visibility = if (hideHeader) View.GONE else View.VISIBLE
        pathBar.visibility = if (hideHeader) View.GONE else View.VISIBLE
    }
}
