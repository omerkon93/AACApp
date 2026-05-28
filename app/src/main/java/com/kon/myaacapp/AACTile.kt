package com.kon.myaacapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "aac_tiles")
@Serializable
data class AACTile(
    @PrimaryKey val id: String,

    // 1. Base Text & Speech
    val label: String,            // Display text
    val ttsText: String,          // Phonetic/engine text

    // 2. Gender Localization (Hebrew support based on User's profile)
    val labelFeminine: String? = null,
    val ttsTextFeminine: String? = null,

    // 3. Media (Audio & Visuals)
    val emoji: String? = null,
    val audioUri: String? = null, // Custom recorded voice (Voice Banking)
    val imageUri: String? = null, // Custom photos/symbols (Boardmaker/PCS/Custom)
    val backgroundColorHex: String? = null, // For Fitzgerald Key color coding

    // 4. Grammar
    val partOfSpeech: String? = null,     // e.g., "VERB", "NOUN", "PRONOUN"
    val grammaticalGender: String? = null, // "M" or "F" (for matching adjectives)

    // 5. Navigation & Linking
    val isCategory: Boolean,              // True if it ONLY opens a folder
    val parentId: String? = null,         // The folder this tile lives inside
    val linkedCategoryId: String? = null, // Adds word to sentence AND opens this folder

    // 6. Layout & Motor Planning
    val cellIndex: Int? = null,   // Fixed position on the grid (prevents shifting)
    val sortOrder: Int = 0,       // Fallback ordering if cellIndex is null

    // 7. Behavioral & State
    val isQuickFire: Boolean = false, // Speaks instantly, doesn't add to sentence strip
    val isHidden: Boolean = false,    // Hides from UI without deleting
    val clickCount: Int = 0           // Analytics for therapists/parents
)