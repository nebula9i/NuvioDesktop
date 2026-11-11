package com.nuvio.app.core.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.Tag
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop file sink: stderr goes nowhere for a jpackaged GUI app, so anything we need to
 * diagnose a running build (stream refreshes, controls pushes, render probes) is appended here
 * instead: %APPDATA%\Nuvio\logs\nuvio.log
 */
object DesktopFileLogWriter : LogWriter() {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val lock = Any()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    private val file: File by lazy {
        val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Nuvio/logs")
        dir.mkdirs()
        val target = File(dir, "nuvio.log")
        if (target.length() > MAX_BYTES) {
            runCatching { File(dir, "nuvio-previous.log").delete() }
            target.renameTo(File(dir, "nuvio-previous.log"))
        }
        target
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        runCatching {
            synchronized(lock) {
                file.appendText(
                    time.format(Date()) + " " + severity.name.padEnd(5) + " " + tag + ": " +
                        message + "\n"
                )
                throwable?.let {
                    file.appendText("    " + it.stackTraceToString().take(2000).replace("\n", "\n    ") + "\n")
                }
            }
        }
    }

    fun note(message: String) {
        log(Severity.Debug, message, "Diag", null)
    }
}