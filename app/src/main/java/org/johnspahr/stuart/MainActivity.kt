package org.johnspahr.stuart

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), ReaderMenuBottomSheet.Listener {

    private lateinit var webView: WebView
    private lateinit var documentParser: DocumentParser
    private lateinit var searchLayout: View
    private lateinit var findTxt: EditText
    private var currentUri: Uri? = null
    private var settings = ReaderSettings()

    private val PREFS_NAME = "ReaderPrefs"
    private val KEY_LAST_URI = "last_uri"
    private val KEY_FONT_SIZE = "font_size"
    private val KEY_THEME = "theme"
    private val KEY_FONT_FAMILY = "font_family"
    private var isSearching = false

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleNewUri(uri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.readerView)

        webView.loadDataWithBaseURL(
            null,
            "<!DOCTYPE html><html><body style='text-align: center; padding: 16px;'><h1>Stuart Text Reader</h1><hr><h4>Open PDF or EPUB file in menu</h4></body></html>",
            "text/html",
            "UTF-8",
            null
        ) // default placeholder html before PDF/EPUB opens to instruct the user

        webView.settings.javaScriptEnabled = true

        documentParser = DocumentParser(this)

        loadSettings() // load user preferences

        searchLayout = findViewById(R.id.searchLayout)

        findTxt = findViewById(R.id.findTxt)

        // if menu FAB pressed
        findViewById<FloatingActionButton>(R.id.menuBtn).setOnClickListener {
            //close find
            closeSearchLayout()

            //open menu sheet at bottom of screen
            val bottomSheet = ReaderMenuBottomSheet()
            bottomSheet.setListener(this)
            bottomSheet.show(supportFragmentManager, "ReaderMenu")
        }

        // if find layout is closed
        findViewById<ImageButton>(R.id.endFindBtn).setOnClickListener {
            closeSearchLayout()
        }

        // search for next instance of text
        findViewById<ImageButton>(R.id.findTxtBtn).setOnClickListener {
            findNext(findTxt.text.toString())
        }

        //stop current search when search box text is changed
        findTxt.addTextChangedListener {
            webView.findAll("")
            isSearching = false
        }

        // override enter keypress in find box
        findTxt.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // find query when user hits enter in text search field
                findNext(v.text.toString())

                // Hide keyboard
                hideKeys()

                true // if enter key was pressed, it's been handled
            } else {
                false // otherwise, do nothing
            }
        }

        // handle intent if opened via share/view
        handleIntent(intent)

        // if no intent, try loading last opened document
        if (currentUri == null) {
            val lastUriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_URI, null)
            if (lastUriStr != null) {
                handleNewUri(lastUriStr.toUri(), persist = false)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    // if finding text, override back press to end ongoing search; otherwise, handle normally
                    searchLayout.isVisible -> {
                        closeSearchLayout()
                    }

                    else -> {
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun findNext(query: String) {
        if (!isSearching) {
            // start search
            webView.findAll(query)
            isSearching = true
        } else {
            // if already searching, find next instance
            webView.findNext(true)
        }
    }

    private fun closeSearchLayout() {
        searchLayout.visibility = View.GONE
        isSearching = false
        webView.findAll("") // stop active search

        // hide keyboard
        hideKeys()

        findTxt.text.clear() // reset search bar text
    }

    private fun hideKeys() {
        // hide soft keyboard
        val imm = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(findTxt.windowToken, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // when file is shared to app
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            val uri = if (intent.action == Intent.ACTION_SEND) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            } else {
                intent.data
            }
            uri?.let { handleNewUri(it) }
        }
    }

    private fun handleNewUri(uri: Uri, persist: Boolean = true) {
        currentUri = uri
        if (persist) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveLastUri(uri)
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Error: missing permissions", Toast.LENGTH_SHORT).show()
            }
        }
        loadDocumentFromUri(uri)
    }

    private fun loadDocumentFromUri(uri: Uri) {
        // parse pdf or epub
        lifecycleScope.launch {
            try {
                val mimeType = contentResolver.getType(uri)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val htmlContent = if (mimeType == "application/epub+zip") {
                        documentParser.parseEpub(inputStream, settings)
                    } else {
                        documentParser.parsePDF(inputStream, settings)
                    }
                    // load returned html stripped of everything
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Failed to parse document", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // ReaderMenuBottomSheet.Listener implementation
    override fun onOpenNewFileRequested() {
        openDocumentLauncher.launch(arrayOf("application/pdf", "application/epub+zip"))
    }

    override fun onSettingsChanged(newSettings: ReaderSettings) {
        // prep and write updated settings to database
        settings = newSettings
        saveSettings()
        currentUri?.let { loadDocumentFromUri(it) }
    }

    override fun getCurrentSettings(): ReaderSettings = settings

    override fun showFindText() {
        // show find text layout
        searchLayout.visibility = View.VISIBLE
        findTxt.requestFocus()
    }

    private fun saveSettings() {
        // write settings to storage
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putFloat(KEY_FONT_SIZE, settings.fontSizeMultiplier)
            putString(KEY_THEME, settings.theme.name)
            putString(KEY_FONT_FAMILY, settings.fontFamily)
            apply()
        }
    }

    private fun loadSettings() {
        // retrieve user prefs for theme, font, etc
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val fontSize = prefs.getFloat(KEY_FONT_SIZE, 1.0f)
        val themeName = prefs.getString(KEY_THEME, ReaderTheme.DEFAULT.name)
        val fontFamily = prefs.getString(KEY_FONT_FAMILY, "sans-serif") ?: "sans-serif"
        val theme = try {
            ReaderTheme.valueOf(themeName!!)
        } catch (_: Exception) {
            ReaderTheme.DEFAULT
        }
        settings = ReaderSettings(fontSize, theme, fontFamily)
    }

    private fun saveLastUri(uri: Uri) {
        // for resuming reading when app is reopened
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString(KEY_LAST_URI, uri.toString())
            apply()
        }
    }
}