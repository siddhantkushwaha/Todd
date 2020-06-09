package com.siddhantkushwaha.todd

import java.util.concurrent.Callable

class DownloadTask(
    private val gDrive: GDrive,
    private val fileId: String,
    private val filePath: String?,
    private val firstBytePos: Long?,
    private val lastBytePos: Long?
) : Callable<ByteArray?> {

    override fun call(): ByteArray? {
        val result = gDrive.download(fileId, filePath, firstBytePos, lastBytePos)
        println("Downloaded chunk $firstBytePos-$lastBytePos for $fileId.")
        return result
    }
}