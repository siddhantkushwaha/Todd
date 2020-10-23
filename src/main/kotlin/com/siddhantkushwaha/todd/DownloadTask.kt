package com.siddhantkushwaha.todd

import java.nio.file.Path
import java.util.concurrent.Callable

class DownloadTask(
        private val gDrive: GDrive,
        private val fileId: String,
        private val filePath: Path?,
        private val firstBytePos: Long?,
        private val lastBytePos: Long?
) : Callable<ByteArray?> {

    override fun call(): ByteArray? {
        val result = gDrive.downloadFile(fileId, filePath, firstBytePos, lastBytePos)
        println("Downloaded chunk $firstBytePos-$lastBytePos for $fileId.")
        return result
    }
}