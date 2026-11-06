package org.rigacci.olyshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsFragment : PreferenceFragmentCompat() {

    // SAF folder picker (ACTION_OPEN_DOCUMENT_TREE under the hood).
    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                // Keep access across reboots.
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putString(Prefs.SAF_TREE_URI, uri.toString()).apply()
                updateFolderSummary()
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Numeric keyboard for the cache-days field.
        findPreference<EditTextPreference>(Prefs.CACHE_MAX_DAYS)?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }

        // "Download folder": tappable only in SAF mode; opens the picker.
        val folderPref = findPreference<Preference>("download_folder")
        folderPref?.setOnPreferenceClickListener { pickFolder.launch(null); true }

        val modePref = findPreference<ListPreference>(Prefs.DOWNLOAD_MODE)
        folderPref?.isEnabled = (modePref?.value == "saf")
        modePref?.setOnPreferenceChangeListener { _, newValue ->
            val saf = (newValue as String) == "saf"
            folderPref?.isEnabled = saf
            // First time switching to SAF with no folder yet: ask right away.
            if (saf && Prefs.safTreeUri(requireContext()) == null) pickFolder.launch(null)
            true
        }

        updateFolderSummary()
    }

    private fun updateFolderSummary() {
        val pref = findPreference<Preference>("download_folder") ?: return
        val uriStr = Prefs.safTreeUri(requireContext())
        pref.summary = if (uriStr == null) "Not set" else treeUriToLabel(uriStr)
    }

    /** Turn a tree Uri into something readable, e.g. "primary:Download/OlyShare" -> "Download/OlyShare". */
    private fun treeUriToLabel(uriStr: String): String =
        try {
            val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriStr))
            docId.substringAfter(':', docId)
        } catch (e: Exception) {
            uriStr
        }
}
