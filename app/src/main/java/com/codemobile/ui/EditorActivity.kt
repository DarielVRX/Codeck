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
import androidx.appcompat.app.AppCompatActivity
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
 * Activity única de la app (MVP): tabbar + explorador + editor + consola +
 * barra de controles. El teclado del sistema se maneja con adjustResize
 * (ver manifest), así que la barra de controles queda automáticamente
 * pegada encima de él sin lógica adicional.
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
    private lateinit var folderButton: TextView
    private lateinit var rotateButton: TextView

    private var consoleOutputView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        bindViews()
        setupTabs()
        setupExplorer()
        setupControlButtons()
        setupKeyboardVisibilityWatcher()

        if (!PermissionHelper.hasStorageAccess()) {
            showPermissionDialog()
        }

        if (openFiles.isEmpty()) {
            addNewTab(OpenFile.untitled(colorIndex = 0))
        }
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
        folderButton = findViewById(R.id.folderButton)
        rotateButton = findViewById(R.id.rotateButton)
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
    }

    private fun switchToTab(index: Int) {
        if (index !in openFiles.indices) return
        saveCurrentEditorStateIntoModel()
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
        val swatchSize = (32 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

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

        folderButton.setOnClickListener {
            val showing = explorerContainer.visibility == View.VISIBLE
            if (showing) {
                explorerContainer.visibility = View.GONE
                editorContainer.visibility = View.VISIBLE
            } else {
                refreshExplorer()
                explorerContainer.visibility = View.VISIBLE
                editorContainer.visibility = View.GONE
            }
        }
    }

    private fun refreshExplorer() {
        explorerAdapter.update(FileRepository.list(currentDir))
    }

    private fun onExplorerItemClick(file: File) {
        if (file.isDirectory) {
            currentDir = file
            refreshExplorer()
        } else {
            saveCurrentEditorStateIntoModel()
            val openFile = OpenFile.fromFile(file, colorIndex = openFiles.size % paletteSize())
            addNewTab(openFile)
            explorerContainer.visibility = View.GONE
            editorContainer.visibility = View.VISIBLE
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

    // ---------- Botones de la tabbar: Run, carpeta, rotar ----------

    private fun setupControlButtons() {
        runButton.setOnClickListener { runCurrentFile() }
        rotateButton.setOnClickListener { toggleLandscape() }
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
        saveCurrentEditorStateIntoModel()
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
            consoleContainer.addView(view)
        }
        consoleContainer.visibility = View.VISIBLE
    }

    private fun appendConsole(text: String) {
        consoleOutputView?.append(text)
    }

    // ---------- Landscape: ocultar header mientras el teclado está activo ----------

    private fun setupKeyboardVisibilityWatcher() {
        val rootView = findViewById<View>(R.id.rootLayout)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var wasKeyboardOpen = false

            override fun onGlobalLayout() {
                val rect = android.graphics.Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootView.rootView.height
                val keypadHeight = screenHeight - rect.bottom
                val keyboardOpen = keypadHeight > screenHeight * 0.15

                if (keyboardOpen != wasKeyboardOpen) {
                    wasKeyboardOpen = keyboardOpen
                    applyHeaderVisibility(keyboardOpen)
                }
            }
        })
    }

    private fun applyHeaderVisibility(keyboardOpen: Boolean) {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val hideHeader = isLandscape && keyboardOpen
        tabBarRow.visibility = if (hideHeader) View.GONE else View.VISIBLE
        pathBar.visibility = if (hideHeader) View.GONE else View.VISIBLE
    }
}
