package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.fredhappyface.ewesticker.utilities.Toaster
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** MainActivity class inherits from the AppCompatActivity class - provides the settings view */
class MainActivity : AppCompatActivity() {
	// onCreate
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var contextView: View
	private lateinit var toaster: Toaster

	/**
	 * Sets up content view, shared prefs, etc.
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		// Inflate view
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val markwon: Markwon = Markwon.create(this)
		val featuresText = findViewById<TextView>(R.id.features_text)
		markwon.setMarkdown(featuresText, getString(R.string.features_text))

		val linksText = findViewById<TextView>(R.id.links_text)
		markwon.setMarkdown(linksText, getString(R.string.links_text))


		// Set late-init attrs
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.contextView = findViewById(R.id.activityMainRoot)
		this.toaster = Toaster(baseContext)
		refreshStickerDirPath()
		// Update UI with config
		seekBar(findViewById(R.id.iconsPerXSb), findViewById(R.id.iconsPerXLbl), "iconsPerX", 3)
		seekBar(findViewById(R.id.iconSizeSb), findViewById(R.id.iconSizeLbl), "iconSize", 80, 20)
		toggle(findViewById(R.id.showBackButton), "showBackButton", true) {}
		toggle(findViewById(R.id.vertical), "vertical") { isChecked: Boolean ->
			findViewById<SeekBar>(R.id.iconSizeSb).isEnabled = !isChecked
		}
		toggle(findViewById(R.id.restoreOnClose), "restoreOnClose", false) {}
		toggle(findViewById(R.id.scroll), "scroll", false) {}
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = sharedPreferences.edit()
				val uri = result.data?.data
				val stickerDirPath = result.data?.data.toString()
				val contentResolver = applicationContext.contentResolver

				val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
						Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				if (uri != null) {
					contentResolver.takePersistableUriPermission(uri, takeFlags)
				}

				editor.putString("stickerDirPath", stickerDirPath)
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshStickerDirPath()
				importStickers(stickerDirPath)
			}
		}

	/**
	 * Called on button press to launch settings
	 *
	 * @param ignoredView: View
	 */
	fun enableKeyboard(ignoredView: View) {
		val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
		startActivity(intent)
	}

	/**
	 * Called on button press to choose a new directory
	 *
	 * @param ignoredView: View
	 */
	fun chooseDir(ignoredView: View) {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * reloadStickers
	 *
	 * Call this function when a user taps the reload stickers button. If we have a set stickerDirPath, call importStickers()
	 *
	 * @param ignoredView: View
	 */
	fun reloadStickers(ignoredView: View) {
		val stickerDirPath = this.sharedPreferences.getString(
			"stickerDirPath", null
		)
		if (stickerDirPath != null) {
			importStickers(stickerDirPath)
		} else {
			this.toaster.toast(
				getString(R.string.imported_034)
			)
		}
	}

	/** Import files from storage to internal directory */
	private fun importStickers(stickerDirPath: String) {
		toaster.toast(getString(R.string.imported_010))
		val button = findViewById<Button>(R.id.updateStickerPackInfoBtn)
		val button2 = findViewById<Button>(R.id.reloadStickerPackInfoBtn)
		val progressBar = findViewById<LinearProgressIndicator>(R.id.linearProgressIndicator)
		button.isEnabled = false
		button2.isEnabled = false

		lifecycleScope.launch(Dispatchers.IO) {
			val totalStickers =
				StickerImporter(baseContext, toaster, progressBar).importStickers(stickerDirPath)

			withContext(Dispatchers.Main) {
				toaster.toastOnState(
					arrayOf(
						getString(R.string.imported_020, totalStickers),
						getString(R.string.imported_031, totalStickers),
						getString(R.string.imported_032, totalStickers),
						getString(R.string.imported_033, totalStickers),
					)
				)
				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", totalStickers)
				editor.apply()
				refreshStickerDirPath()
				button.isEnabled = true
				button2.isEnabled = true
			}
		}
	}

	/**
	 * Add toggle logic for each toggle/ checkbox in the layout
	 *
	 * @param compoundButton CompoundButton
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Boolean - default value (default=false)
	 * @param callback (Boolean) -> Unit - Add custom behaviour with a callback - for instance to
	 * disable some options
	 */
	private fun toggle(
		compoundButton: CompoundButton,
		sharedPrefKey: String,
		sharedPrefDefault: Boolean = false,
		callback: (Boolean) -> Unit
	) {
		compoundButton.isChecked =
			this.sharedPreferences.getBoolean(sharedPrefKey, sharedPrefDefault)
		callback(compoundButton.isChecked)
		compoundButton.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
			showChangedPrefText()
			callback(compoundButton.isChecked)
			val editor = this.sharedPreferences.edit()
			editor.putBoolean(sharedPrefKey, isChecked)
			editor.apply()
		}
	}

	/**
	 * Add seekbar logic for each seekbar in the layout
	 *
	 * @param seekBar SeekBar
	 * @param seekBarLabel TextView - the label with a value updated when the progress is changed
	 * @param sharedPrefKey String - Id/Key of the SharedPreferences to update
	 * @param sharedPrefDefault Int - default value
	 * @param multiplier Int - multiplier (used to update SharedPreferences and set the
	 * seekBarLabel)
	 */
	private fun seekBar(
		seekBar: SeekBar,
		seekBarLabel: TextView,
		sharedPrefKey: String,
		sharedPrefDefault: Int,
		multiplier: Int = 1
	) {
		seekBarLabel.text =
			this.sharedPreferences.getInt(sharedPrefKey, sharedPrefDefault).toString()
		seekBar.progress =
			this.sharedPreferences.getInt(sharedPrefKey, sharedPrefDefault) / multiplier
		seekBar.setOnSeekBarChangeListener(
			object : OnSeekBarChangeListener {
				var progressMultiplier = sharedPrefDefault
				override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
					progressMultiplier = progress * multiplier
					seekBarLabel.text = progressMultiplier.toString()
				}

				override fun onStartTrackingTouch(seekBar: SeekBar) {}
				override fun onStopTrackingTouch(seekBar: SeekBar) {
					val editor = sharedPreferences.edit()
					editor.putInt(sharedPrefKey, progressMultiplier)
					editor.apply()
					showChangedPrefText()
				}
			})
	}

	/** Reads saved sticker dir path from preferences */
	private fun refreshStickerDirPath() {
		findViewById<TextView>(R.id.stickerPackInfoPath).text =
			this.sharedPreferences.getString(
				"stickerDirPath", resources.getString(R.string.update_sticker_pack_info_path)
			)
		findViewById<TextView>(R.id.stickerPackInfoDate).text =
			this.sharedPreferences.getString(
				"lastUpdateDate", resources.getString(R.string.update_sticker_pack_info_date)
			)
		findViewById<TextView>(R.id.stickerPackInfoTotal).text =
			this.sharedPreferences.getInt("numStickersImported", 0).toString()
	}

	/** Reusable function to warn about changing preferences */
	internal fun showChangedPrefText() {
		this.toaster.toast(
			getString(R.string.pref_000)
		)
	}
}
