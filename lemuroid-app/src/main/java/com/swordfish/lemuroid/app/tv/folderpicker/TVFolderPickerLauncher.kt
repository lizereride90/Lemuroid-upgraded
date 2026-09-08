package com.swordfish.lemuroid.app.tv.folderpicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.io.File

class TVFolderPickerLauncher : ImmersiveActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            startActivityForResult(Intent(this, TVFolderPickerActivity::class.java), REQUEST_CODE_PICK_FOLDER)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val preferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.extras?.getString(TVFolderPickerActivity.RESULT_DIRECTORY_PATH)

            if (newValue.toString() != currentValue) {
                sharedPreferences.edit().apply {
                    this.putString(preferenceKey, newValue.toString())
                    this.commit()
                }

                createSystemFolders(newValue)
            }

            startLibraryIndexWork()
        }
        finish()
    }

    private fun createSystemFolders(path: String?) {
        if (path.isNullOrBlank()) return
        val parent = File(path)
        SYSTEM_FOLDERS.forEach { name ->
            File(parent, name).mkdirs()
        }
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        private val SYSTEM_FOLDERS =
            listOf(
                "nes", "snes", "genesis", "gameboy", "gameboycolor", "gba", "n64",
                "mastersystem", "gamegear", "psx", "ps2", "psp", "nds", "3ds",
                "atari2600", "atari7800", "lynx", "segacd", "neogeopocket",
                "wonderswan", "wonderswancolor", "dos", "fbneo", "mame",
                "pcengine",
            )

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, TVFolderPickerLauncher::class.java))
        }
    }
}
