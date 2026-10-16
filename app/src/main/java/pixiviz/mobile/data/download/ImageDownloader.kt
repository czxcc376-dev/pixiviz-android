package pixiviz.mobile.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pixiviz.mobile.data.remote.Illust
import java.io.File

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

data class DownloadTask(
    val illustId: Long,
    val title: String,
    val page: Int,
    val pageCount: Int,
    val state: DownloadState = DownloadState.QUEUED,
    val message: String? = null,
)

/**
 * Saves pixiv images straight into the system gallery.
 *
 * On API 29+ nothing is written through the legacy permission path: rows are inserted into
 * MediaStore under `Pictures/Pixiviz`, so no storage permission is ever requested.
 */
class ImageDownloader(
    context: Context,
    private val client: OkHttpClient,
) {

    private val appContext = context.applicationContext

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING } }
    }

    /** Downloads every page of [illust]; urls must already be resolved for the active channel. */
    suspend fun downloadIllust(illust: Illust, resolve: (String) -> String) {
        val pages = illust.pages
        val tasks = pages.mapIndexed { index, urls ->
            val best = urls.original.ifBlank { urls.large.ifBlank { urls.medium } }
            DownloadTask(illust.id, illust.title, index + 1, pages.size)
        }
        _tasks.update { it + tasks }

        tasks.forEachIndexed { index, task ->
            update(task) { it.copy(state = DownloadState.RUNNING) }
            val raw = pages[index].let { it.original.ifBlank { it.large.ifBlank { it.medium } } }
            val result = save(resolve(raw), fileName(illust, index + 1, raw))
            update(task) {
                result.fold(
                    onSuccess = { it.copy(state = DownloadState.DONE) },
                    onFailure = { e -> it.copy(state = DownloadState.FAILED, message = e.message) },
                )
            }
        }
    }

    private fun update(task: DownloadTask, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it == task) transform(it) else it } }
    }

    private fun fileName(illust: Illust, page: Int, url: String): String {
        val ext = url.substringAfterLast('.', "jpg").substringBefore('?').take(4).ifBlank { "jpg" }
        val safeTitle = illust.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "illust" }
        return "${illust.id}_${safeTitle}_p${page - 1}.$ext"
    }

    private suspend fun save(url: String, name: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank()) error("图片地址为空")
            val request = Request.Builder().url(url).build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.bytes() ?: error("响应为空")
            }
            insert(bytes, name)
        }
    }

    private fun insert(bytes: ByteArray, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeOf(name))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Pixiviz")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: mimeOf(name).let { error("无法在相册中创建条目") }

        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入相册")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** Legacy location helper, kept for the "open folder" affordance in Settings. */
    fun galleryFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pixiviz")
}
