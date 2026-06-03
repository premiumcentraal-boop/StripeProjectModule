package com.projectstripe.manager

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProjectStripeLogger {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    val entries = mutableStateListOf<ProjectStripeLogEntry>()

    fun log(message: String) {
        entries.add(0, ProjectStripeLogEntry(formatter.format(Date()), message))
    }

    fun exportText(): String {
        if (entries.isEmpty()) return "Project Stripe logs are empty."
        return entries.reversed().joinToString(separator = "\n") { "[${it.time}] ${it.message}" }
    }
}

data class ProjectStripeLogEntry(
    val time: String,
    val message: String,
)
