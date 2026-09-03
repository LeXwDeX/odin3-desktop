package com.odin.desktop.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_mappings",
    foreignKeys = [
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("tabId"),
        Index(value = ["tabId", "packageName"], unique = true)
    ]
)
data class AppMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tabId: Long,
    val packageName: String,
    val sortOrder: Int = 0,
    val customLabel: String? = null,
    val isHidden: Boolean = false
)
