package org.johnspahr.stuart

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import kotlin.math.abs

// set default values
data class ReaderSettings(
    val fontSizeMultiplier: Float = 1.0f,
    val theme: ReaderTheme = ReaderTheme.DEFAULT,
    val fontFamily: String = "sans-serif"
)

// color schemes
enum class ReaderTheme(val backgroundColor: String, val textColor: String) {
    DEFAULT("#fff", "#000"),
    DARK("#121212", "#e0e0e0"),
    SEPIA("#f4ecd8", "#5b4636"),
    BLUE("#1A1C38", "#fff"),
    TERMINAL("#000", "#00ff00")
}

class DocumentParser(context: Context) {
    init {
        PDFBoxResourceLoader.init(context) // start up PDFBox library
    }

    suspend fun parsePDF(
        inputStream: InputStream,
        settings: ReaderSettings = ReaderSettings()
    ): String = withContext(Dispatchers.IO) {
        try {
            // attempt to open pdf
            val doc = PDDocument.load(inputStream)
            val stripper = HtmlStripper(settings.fontSizeMultiplier)
            val htmlBody = stripper.getText(doc)
            doc.close()
            wrapInTemplate(
                htmlBody,
                settings
            ) // pass text-only html extracted from pdf to be displayed in webview; also pass through user prefs
        } catch (e: Exception) {
            e.printStackTrace()
            wrapInTemplate("<p>Error parsing PDF: ${e.message}</p>", settings)
        }
    }

    suspend fun parseEpub(
        inputStream: InputStream,
        settings: ReaderSettings = ReaderSettings()
    ): String = withContext(Dispatchers.IO) {
        try {
            // try to open epub
            val book = EpubReader().readEpub(inputStream)
            val htmlBuilder = StringBuilder()

            for (spineReference in book.spine.spineReferences) {
                val resource = spineReference.resource
                val data = resource.data
                val encoding = resource.inputEncoding ?: "UTF-8"
                val rawHtml = String(data, charset(encoding))

                // clean the HTML using Jsoup
                val doc: Document = Jsoup.parse(rawHtml)
                val body = doc.body().html()

                // finalize cleaning
                val cleanBody = Jsoup.clean(body, Safelist.basicWithImages())

                // add text to html
                htmlBuilder.append(cleanBody)
            }

            wrapInTemplate(
                htmlBuilder.toString(),
                settings
            ) // send text-only HTML parsed from EPUB and user preferences to be displayed in webview
        } catch (e: Exception) {
            e.printStackTrace()
            wrapInTemplate("<p>Error parsing EPUB: ${e.message}</p>", settings)
        }
    }

    private fun wrapInTemplate(bodyContent: String, settings: ReaderSettings): String {
        // return styled html (with user preferences)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: ${settings.fontFamily}; 
                        padding: 16px 16px 48px 16px; 
                        line-height: 1.6; 
                        background-color: ${settings.theme.backgroundColor};
                        color: ${settings.theme.textColor};
                        font-size: ${16 * settings.fontSizeMultiplier}px;
                    }
                    p { margin-bottom: 1.2em; }
                    span { display: inline-block; }
                    img { max-width: 100%; height: auto; }
                    h1, h2, h3, h4, h5, h6, a { color: ${settings.theme.textColor}; }
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }

    private class HtmlStripper(private val fontSizeMultiplier: Float) : PDFTextStripper() {
        private val htmlBuilder = StringBuilder()

        init {
            super.setSortByPosition(true)
        }

        @Throws(IOException::class)
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            // handle font size slider adjustments in real time
            if (textPositions.isEmpty()) {
                return
            }

            htmlBuilder.append("<p>")
            var lastFontSize = -1f

            for (tp in textPositions) {
                val fontSize = tp.fontSizeInPt * fontSizeMultiplier
                if (abs(fontSize - lastFontSize) > 0.1f) {
                    if (lastFontSize != -1f) {
                        htmlBuilder.append("</span>")
                    }
                    htmlBuilder.append("<span style=\"font-size: ${fontSize}pt;\">")
                    lastFontSize = fontSize
                }

                // Escape HTML characters
                when (val char = tp.unicode) {
                    "<" -> htmlBuilder.append("&lt;")
                    ">" -> htmlBuilder.append("&gt;")
                    "&" -> htmlBuilder.append("&amp;")
                    else -> htmlBuilder.append(char)
                }
            }

            if (lastFontSize != -1f) {
                htmlBuilder.append("</span>")
            }
            htmlBuilder.append("</p>")
        }

        override fun getText(doc: PDDocument): String {
            val writer = StringWriter()
            output = writer
            writeText(doc, writer)
            return htmlBuilder.toString()
        }
    }
}