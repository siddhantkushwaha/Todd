package com.siddhantkushwaha.todd.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


fun pack(sourcePath: String, zipFilePath: String? = null): String {
    val sourcePathObj = Paths.get(sourcePath)
    val zipFilePathObj =
            if (zipFilePath != null)
                Paths.get(zipFilePath)
            else
                Paths.get(sourcePathObj.parent.toString(), "${sourcePathObj.fileName}.zip")

    Files.createFile(zipFilePathObj)
    ZipOutputStream(Files.newOutputStream(zipFilePathObj)).use { zs: ZipOutputStream ->
        Files.walk(sourcePathObj)
                .filter { path: Path -> !Files.isDirectory(path) }
                .forEach { path: Path ->
                    val zipEntry = ZipEntry(sourcePathObj.relativize(path).toString())
                    try {
                        zs.putNextEntry(zipEntry)
                        Files.copy(path, zs)
                        zs.closeEntry()
                    } catch (e: IOException) {
                        System.err.println(e)
                    }
                }
    }

    return zipFilePathObj.toString()
}


