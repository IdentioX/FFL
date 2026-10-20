package ru.ffl24.direct

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File
import android.os.Bundle
import android.util.Base64

/** Fresh JUnit XML for direct am instrument runs (including failures and skipped cases). */
class CompletionRunListener: RunListener() {
    private data class Case(val description: Description,var failure: String?=null,var skipped: Boolean=false)
    private val cases=linkedMapOf<String,Case>()
    private fun key(d: Description)="${d.className}#${d.methodName}"
    override fun testRunStarted(description: Description) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val previous=File(context.getExternalFilesDir(null),"completion-ui")
        val recovery=File(context.filesDir,"completion-initial-recovered")
        if(previous.exists() && !recovery.exists()) runCatching { previous.copyRecursively(recovery) }
    }
    override fun testStarted(description: Description) { cases[key(description)]=Case(description) }
    override fun testFailure(failure: Failure) { cases.getOrPut(key(failure.description)) { Case(failure.description) }.failure=failure.trace }
    override fun testAssumptionFailure(failure: Failure) { cases.getOrPut(key(failure.description)) { Case(failure.description) }.skipped=true }
    override fun testIgnored(description: Description) { cases[key(description)]=Case(description,skipped=true) }
    override fun testRunFinished(result: Result) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun escape(s: String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
        val xml=buildString {
            append("<testsuite tests=\"${cases.size}\" failures=\"${cases.values.count { it.failure!=null }}\" errors=\"0\" skipped=\"${cases.values.count { it.skipped }}\" time=\"${result.runTime/1000.0}\">")
            for(case in cases.values) {
                append("<testcase classname=\"${escape(case.description.className.orEmpty())}\" name=\"${escape(case.description.methodName.orEmpty())}\">")
                if(case.skipped) append("<skipped/>")
                case.failure?.let { append("<failure>${escape(it)}</failure>") }
                append("</testcase>")
            }
            append("</testsuite>")
        }
        // A non-debuggable R8 target cannot be read through run-as. Instrumentation
        // runs as its UID, so another package's private files are not an output path.
        if(InstrumentationRegistry.getArguments().getString("auditReportToStream")=="true") {
            instrumentation.sendStatus(0,Bundle().apply {
                putString("fflAuditJUnitBase64",Base64.encodeToString(xml.toByteArray(Charsets.UTF_8),Base64.NO_WRAP))
            })
        } else {
            val directory=File(instrumentation.targetContext.filesDir,"completion-ui").apply { mkdirs() }
            File(directory,"instrumentation.xml").writeText(xml)
        }
    }
}
