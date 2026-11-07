package com.maejung.phone.store

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrefsTest {
    @Test
    fun `saved pairing is available to a fresh store as a complete snapshot`() {
        val preferences = MemoryPreferences()
        val pairing = StoredPairing("r_one", "v_one", "signed.token", "김  민지")
        Prefs(preferences).save(pairing)
        assertEquals(pairing, Prefs(preferences).load())
        assertEquals(1, preferences.writes, "one editor transaction must persist the whole pairing")
    }

    @Test
    fun `clear removes the pairing from future store instances`() {
        val preferences = MemoryPreferences()
        Prefs(preferences).save(StoredPairing("r_one", "v_one", "signed.token", "민지"))
        assertEquals("r_one", Prefs(preferences).load()?.riderId)
        Prefs(preferences).clear()
        assertNull(Prefs(preferences).load())
    }

    @Test
    fun `corrupt incomplete or blank persisted records cannot restore a pairing`() {
        val preferences = MemoryPreferences()
        Prefs(preferences).save(StoredPairing("r_one", "v_one", "signed.token", "민지"))
        assertEquals("r_one", Prefs(preferences).load()?.riderId)
        for (value in listOf("broken JSON", "{}", """{"riderId":"r_one","vehicleId":"v_one","riderToken":"","riderName":"민지"}""")) {
            preferences.values.replaceAll { _, _ -> value }
            assertNull(Prefs(preferences).load(), value)
        }
    }

    @Test
    fun `saving a replacement never mixes it with the previous pairing`() {
        val preferences = MemoryPreferences()
        val store = Prefs(preferences)
        store.save(StoredPairing("r_old", "v_old", "old.token", "이전"))
        val replacement = StoredPairing("r_new", "v_new", "new.token", "새 이름")
        store.save(replacement)
        assertEquals(replacement, Prefs(preferences).load())
        assertEquals(2, preferences.writes)
    }

    private class MemoryPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        var writes = 0
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var cleared = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = values }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = null }
            override fun clear(): SharedPreferences.Editor = apply { cleared = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (cleared) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                writes++
            }
        }
    }
}
