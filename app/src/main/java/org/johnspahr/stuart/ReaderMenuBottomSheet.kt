package org.johnspahr.stuart

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider


class ReaderMenuBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onOpenNewFileRequested()
        fun onSettingsChanged(settings: ReaderSettings)
        fun getCurrentSettings(): ReaderSettings

        fun showFindText()
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    //show entire sheet for tablets; without this code, it doesn't show the whole bottom sheet menu
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return

        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_reader_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentSettings = listener?.getCurrentSettings() ?: ReaderSettings()

        // open file/dismiss popup menu
        view.findViewById<Button>(R.id.openFileBtn).setOnClickListener {
            listener?.onOpenNewFileRequested()
            dismiss()
        }

        // find text/dismiss popup menu
        view.findViewById<Button>(R.id.showSearchLayoutBtn).setOnClickListener {
            listener?.showFindText()
            dismiss()
        }

        // initiate font size slider
        val slider = view.findViewById<Slider>(R.id.fontSizeSlider)
        slider.value = currentSettings.fontSizeMultiplier

        // handle font size adjustments...
        slider.addOnChangeListener { _, value, _ ->
            val updatedSettings =
                (listener?.getCurrentSettings() ?: currentSettings).copy(fontSizeMultiplier = value)
            listener?.onSettingsChanged(updatedSettings)
        }

        // select current font on startup
        val fontChipGroup = view.findViewById<ChipGroup>(R.id.fontChipGroup)
        when (currentSettings.fontFamily) {
            "serif" -> fontChipGroup.check(R.id.chipSerif)
            "monospace" -> fontChipGroup.check(R.id.chipMono)
            "cursive" -> fontChipGroup.check(R.id.chipCursive)
            "sans-serif-condensed" -> fontChipGroup.check(R.id.chipCondensed)
            else -> fontChipGroup.check(R.id.chipSans)
        }

        // when font changed...
        fontChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val newFontFamily = when (checkedIds.first()) {
                    R.id.chipSerif -> "serif"
                    R.id.chipMono -> "monospace"
                    R.id.chipCursive -> "cursive"
                    R.id.chipCondensed -> "sans-serif-condensed"
                    else -> "sans-serif"
                }
                // save selected font
                val updatedSettings = (listener?.getCurrentSettings() ?: currentSettings).copy(
                    fontFamily = newFontFamily
                )
                listener?.onSettingsChanged(updatedSettings)
            }
        }

        // select current theme in list
        val themeChipGroup = view.findViewById<ChipGroup>(R.id.themeChipGroup)
        when (currentSettings.theme) {
            ReaderTheme.DEFAULT -> themeChipGroup.check(R.id.chipDefault)
            ReaderTheme.DARK -> themeChipGroup.check(R.id.chipDark)
            ReaderTheme.SEPIA -> themeChipGroup.check(R.id.chipSepia)
            ReaderTheme.BLUE -> themeChipGroup.check(R.id.chipBlue)
            ReaderTheme.TERMINAL -> themeChipGroup.check(R.id.chipTerminal)
        }

        // when theme is changed...
        themeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val newTheme = when (checkedIds.first()) {
                    R.id.chipDark -> ReaderTheme.DARK
                    R.id.chipSepia -> ReaderTheme.SEPIA
                    R.id.chipBlue -> ReaderTheme.BLUE
                    R.id.chipTerminal -> ReaderTheme.TERMINAL
                    else -> ReaderTheme.DEFAULT
                }
                //update settings with current selection
                val updatedSettings =
                    (listener?.getCurrentSettings() ?: currentSettings).copy(theme = newTheme)
                listener?.onSettingsChanged(updatedSettings)
            }
        }

        // grab version name to be used in about info
        val aboutBtn = view.findViewById<Button>(R.id.aboutBtn)
        val manager = requireContext().packageManager
        val info = manager.getPackageInfo(
            requireContext().packageName, 0
        )
        val version = info.versionName

        aboutBtn.setOnClickListener {
            // show app info dialog
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Stuart Text Reader")
            builder.setIcon(R.mipmap.ic_launcher)
            builder.setMessage("Version $version\nCreated by John Spahr\njohnspahr.org\n\nLibraries Used:\n• PDFBox: github.com/tomroush/pdfbox-android\n• Epublib: github.com/positiondev/epublib\n• Jsoup: jsoup.org")
            builder.setPositiveButton("Close", null)
            builder.create().show()
        }
    }
}