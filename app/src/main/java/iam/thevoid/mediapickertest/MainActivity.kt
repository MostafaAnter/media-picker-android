package iam.thevoid.mediapickertest

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import iam.thevoid.ae.gone
import iam.thevoid.ae.hide
import iam.thevoid.ae.inflate
import iam.thevoid.ae.show
import iam.thevoid.e.format
import iam.thevoid.e.safe
import iam.thevoid.mediapicker.picker.Picker
import iam.thevoid.mediapicker.picker.Purpose
import iam.thevoid.mediapicker.picker.metrics.MemorySize
import iam.thevoid.mediapicker.picker.metrics.Resolution
import iam.thevoid.mediapicker.picker.metrics.SizeUnit
import iam.thevoid.mediapicker.picker.metrics.VideoQuality
import iam.thevoid.mediapicker.picker.options.ImageOptions
import iam.thevoid.mediapicker.picker.options.VideoOptions
import iam.thevoid.mediapicker.rx1.MediaPicker
import iam.thevoid.mediapicker.rx1.file
import iam.thevoid.mediapicker.util.FileUtil
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.size
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

class MainActivity : AppCompatActivity(), View.OnClickListener, Picker.OnDismissListener {

    private var lastImageOptions: ImageOptions? = null
    private var lastVideoOptions: VideoOptions? = null
    private var lastPurposes: List<Purpose> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pick_image?.setOnClickListener(this)
        pick_video?.setOnClickListener(this)
        take_photo?.setOnClickListener(this)
        take_video?.setOnClickListener(this)
        customize?.setOnClickListener(this)

    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.take_photo ->
                imageOptionsDialog { options ->
                    MediaPicker.builder()
                            .setImageOptions(options)
                            .take(Purpose.Take.Photo)
                            .onDismissAppSelect(this)
                            .onDismissPick(this)
                            .build()
                            .request(this)
                            .compose(loading())
                            .compose(load(::showImage))
                            .subscribe(::showFileInfo) { it.printStackTrace() }
                }

            R.id.pick_image ->
                imageOptionsDialog { options ->
                    MediaPicker.builder()
                            .setImageOptions(options)
                            .pick(Purpose.Pick.Image)
                            .onDismissAppSelect(this)
                            .onDismissPick(this)
                            .build()
                            .request(this)
                            .compose(loading())
                            .compose(load(::showImage))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(::showFileInfo) { it.printStackTrace() }
                }


            R.id.take_video ->
                videoOptionsDialog { options ->
                    MediaPicker.builder()
                            .setTakeVideoOptions(options)
                            .take(Purpose.Take.Video)
                            .onDismissAppSelect(this)
                            .onDismissPick(this)
                            .build()
                            .request(this)
                            .compose(loading())
                            .compose(load(::showVideo))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(::showFileInfo) { it.printStackTrace() }
                }

            R.id.pick_video ->
                MediaPicker.builder()
                        .pick(Purpose.Pick.Video)
                        .onDismissAppSelect(this)
                        .onDismissPick(this)
                        .build()
                        .request(this)
                        .compose(loading())
                        .compose(load(::showVideo))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(::showFileInfo) { it.printStackTrace() }

            R.id.customize -> customOptionsDialog { purposes ->
                MediaPicker.builder()
                        .pick(*purposes.filterIsInstance<Purpose.Pick>().toTypedArray())
                        .take(*purposes.filterIsInstance<Purpose.Take>().toTypedArray())
                        .onDismissAppSelect(this)
                        .onDismissPick(this)
                        .build()
                        .request(this)
                        .compose(loading())
                        .compose(load(::showVideo))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(::showFileInfo) { it.printStackTrace() }
            }
        }
    }

    private fun customOptionsDialog(onCustomized: (List<Purpose>) -> Unit) {
        val view = inflate(R.layout.dialog_custom)
        val pickImageCheckbox = view.findViewById<CheckBox>(R.id.pick_image_checkbox)
        val pickVideoCheckbox = view.findViewById<CheckBox>(R.id.pick_video_checkbox)
        val takePhotoCheckbox = view.findViewById<CheckBox>(R.id.take_photo_checkbox)
        val takeVideoCheckbox = view.findViewById<CheckBox>(R.id.take_video_checkbox)
        pickImageCheckbox.isChecked = lastPurposes.contains(Purpose.Pick.Image)
        pickVideoCheckbox.isChecked = lastPurposes.contains(Purpose.Pick.Video)
        takePhotoCheckbox.isChecked = lastPurposes.contains(Purpose.Take.Photo)
        takeVideoCheckbox.isChecked = lastPurposes.contains(Purpose.Take.Video)
        AlertDialog.Builder(this)
                .setView(view)
                .setMessage(getString(R.string.pick_image_description))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onCustomized(mutableListOf<Purpose>().apply {
                        if (pickImageCheckbox.isChecked)
                            add(Purpose.Pick.Image)
                        if (pickVideoCheckbox.isChecked)
                            add(Purpose.Pick.Video)
                        if (takePhotoCheckbox.isChecked)
                            add(Purpose.Take.Photo)
                        if (takeVideoCheckbox.isChecked)
                            add(Purpose.Take.Video)
                    }.also { lastPurposes = it })
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    private fun imageOptionsDialog(onCustomized: (ImageOptions) -> Unit) {
        val view = inflate(R.layout.dialog_image_options)
        val widthEditText = view.findViewById<EditText>(R.id.width)
        val heightEditText = view.findViewById<EditText>(R.id.height)
        val sizeEditText = view.findViewById<EditText>(R.id.image_size)
        val preserveRatioCheckBox = view.findViewById<CheckBox>(R.id.preserve_ratio)
        lastImageOptions?.apply {
            widthEditText.setText(maxResolution.width.takeIf { it > 0 }?.toString().safe())
            heightEditText.setText(maxResolution.height.takeIf { it > 0 }?.toString().safe())
            sizeEditText.setText(maxSize.kiloBytes.takeIf { it > 0 }?.toString().safe())
            preserveRatioCheckBox.isChecked = preserveRatio
        }
        AlertDialog.Builder(this)
                .setView(view)
                .setMessage(getString(R.string.pick_image_description))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onCustomized(ImageOptions(
                            maxResolution = Resolution(
                                    width = widthEditText.number(),
                                    height = heightEditText.number()
                            ),
                            maxSize = MemorySize(
                                    size = sizeEditText.number(),
                                    unit = SizeUnit.KILOBYTE
                            ),
                            preserveRatio = preserveRatioCheckBox.isChecked
                    ).also { lastImageOptions = it })
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    @OptIn(ExperimentalTime::class)
    private fun videoOptionsDialog(onCustomized: (VideoOptions) -> Unit) {
        val view = inflate(R.layout.dialog_video_options)
        val durationEditText = view.findViewById<EditText>(R.id.duration)
        val sizeEditText = view.findViewById<EditText>(R.id.video_size)
        val qualityRadioGroup = view.findViewById<RadioGroup>(R.id.quality_radio_group)
        lastVideoOptions?.apply {
            durationEditText.setText(maxDuration.inMilliseconds.takeIf { it > 0 }?.toInt()?.toString().safe())
            sizeEditText.setText(maxSize.kiloBytes.takeIf { it > 0 }?.toString().safe())
            qualityRadioGroup.check(when (quality) {
                VideoQuality.LOW -> R.id.low
                VideoQuality.HIGH -> R.id.high
            })
        }
        AlertDialog.Builder(this)
                .setView(view)
                .setMessage(getString(R.string.pick_image_description))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onCustomized(VideoOptions(
                            maxDuration = durationEditText.number().milliseconds,
                            maxSize = MemorySize(
                                    size = sizeEditText.number(),
                                    unit = SizeUnit.KILOBYTE
                            ),
                            quality = when (qualityRadioGroup.checkedRadioButtonId) {
                                R.id.low -> VideoQuality.LOW
                                R.id.high -> VideoQuality.HIGH
                                else -> VideoQuality.HIGH
                            }
                    ).also { lastVideoOptions = it })
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
    }

    private fun filesizeInMb(file: File): String {
        val size = file.length().toDouble()
        return (size / 1024 / 1024).format(2)
    }


    private fun load(show: (Uri) -> Unit): Observable.Transformer<Uri, File> =
            Observable.Transformer { observable ->
                observable.observeOn(AndroidSchedulers.mainThread())
                        .doOnNext { show(it) }
                        .observeOn(Schedulers.io())
                        .compose(file(this))
            }

    private fun showImage(uri: Uri) {
        video?.gone()
        video?.stopPlayback()
        image?.show()
        image?.setImageURI(null)
        image?.setImageURI(uri)
    }

    private fun showVideo(uri: Uri) {
        video?.show()
        image?.gone()
        video?.setVideoURI(uri)
        video?.start()
    }

    @SuppressLint("SetTextI18n")
    private fun showFileInfo(file: File) {
        val exists = file.exists()
        file_info.hide(!exists)
        if (!exists)
            return
        path?.text = "Path: ${file.absolutePath}"
        size?.text = "Size: ${filesizeInMb(file)} MB"
        val isImage = file.extension.let { !FileUtil.isVideoExt(it) }
        resolution?.gone(!isImage)
        if (isImage) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            resolution?.text = "${bitmap.width} X ${bitmap.height}"
        }
    }

    private fun EditText.number() =
            try {
                text.toString().takeIf { it.isNotBlank() }?.toInt() ?: -1
            } catch (e: Exception) {
                -1
            }

    private fun <T> loading() = Observable.Transformer<T, T> {
        it.doOnSubscribe { Handler(Looper.getMainLooper()).post { progress.show() } }
                .doOnTerminate { Handler(Looper.getMainLooper()).post { progress.hide() } }
    }

    override fun onDismiss() = progress.hide()
}