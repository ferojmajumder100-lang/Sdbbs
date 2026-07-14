package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dialog_configs")
data class DialogConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val status: String = "off", // "on" = normal, "off" = dialog/toast active
    val title: String = "Update Available",
    val message: String = "Please update to the latest version.",
    val type: String = "dialog", // "dialog" or "toast"
    val isCancelable: Boolean = false,
    val buttonText: String = "Update Now",
    val actionUrl: String = "",
    val configUrl: String = "",
    val checkInterval: Int = 10, // polling interval in seconds
    val timestamp: Long = System.currentTimeMillis()
)
