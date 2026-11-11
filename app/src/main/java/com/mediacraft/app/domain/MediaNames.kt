package com.mediacraft.app.domain

import java.text.Normalizer
import java.util.Locale

/** Shared external storage may ignore case; preview and execution must reject the same collisions. */
fun normalizedMediaName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
