package app.aemu.core

/**
 * Набор движка: хостовые исполняемые файлы (лежат в nativeLibraryDir как lib*.so)
 * и гостевые файлы (32-битный ARM, копируются в дерево прошивки из assets/engines/<id>).
 *
 * GB — Android 2.x (Gingerbread/Froyo): GL рисуется в pbuffer и читается в fb0, binder и GL-сервер
 *      живут в отдельных процессах-«слотах» приложения.
 * KK — Android 4.x и новее: GL-мост работает прямо в процессе приложения и рисует в Surface.
 */
enum class Engine(
    val id: String,
    val title: String,
    val qemu: String = "",
    val binderd: String = "",
    val glserverd: String = "",
    val runner: String = "",
    val guestGles: String = "",
) {
    /** Native arm64 GSI execution on host ARM64 CPU without translator/QEMU */
    N64(
        id = "n64",
        title = "Native arm64 (GSI)",
    );

    companion object {
        fun forApi(api: Int): Engine = N64
        fun byId(id: String?): Engine = N64
    }
}

