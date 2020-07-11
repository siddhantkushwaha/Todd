package com.siddhantkushwaha.todd

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min
import kotlin.math.pow


class GDrive {

    private val APPLICATION_NAME = "GDrive - Todd"
    private val SCOPES = listOf(DriveScopes.DRIVE)
    private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
    private val CREDENTIALS_FILE_PATH = "/credentials.json"
    private val TOKENS_DIRECTORY_PATH = "tokens"

    /* 32 MB */
    private val CHUNK_SIZE: Long = 32 * 2.0.pow(20).toLong()

    private val service: Drive

    init {
        val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
        service = Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        // Load client secrets.

        val inputStream = GDrive::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
            ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")

        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    public fun getDriveService(): Drive {
        return service
    }

    public fun getFile(fileId: String): com.google.api.services.drive.model.File {
        return service.files().get(fileId).setFields("id, name, size, mimeType").execute()!!
    }

    public fun getFileByQuery(query: String): com.google.api.services.drive.model.File? {
        val files = ArrayList<com.google.api.services.drive.model.File>()
        var pageToken: String? = null
        do {
            val result: FileList = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, size, mimeType)")
                .setPageToken(pageToken)
                .execute()
            for (file in result.files)
                return file
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return null
    }

    public fun getFilesByQuery(query: String): ArrayList<com.google.api.services.drive.model.File> {
        val files = ArrayList<com.google.api.services.drive.model.File>()
        var pageToken: String? = null
        do {
            val result: FileList = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, size, mimeType)")
                .setPageToken(pageToken)
                .execute()
            for (file in result.files)
                files.addAll(result.files)
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    public fun getSize(fileId: String): Long {
        return getFile(fileId).getSize()
    }

    public fun getName(fileId: String): String {
        return getFile(fileId).name
    }

    public fun download(
        fileId: String,
        filePath: String? = null,
        firstBytePos: Long? = null,
        lastBytePos: Long? = null
    ): ByteArray? {
        val request = service.files().get(fileId)
        request.mediaHttpDownloader.isDirectDownloadEnabled = false
        request.mediaHttpDownloader.chunkSize = CHUNK_SIZE.toInt()

        if (firstBytePos != null && lastBytePos != null) {
            // **** Range headers not necessary for direct downloads *****
            // request.requestHeaders.range = "bytes=7-9"

            // this works
            request.mediaHttpDownloader.setContentRange(firstBytePos, lastBytePos.toInt())
        }

        val outputStream = if (filePath == null)
            ByteArrayOutputStream()
        else
            FileOutputStream(filePath)

        request.executeMediaAndDownloadTo(outputStream)

        return if (filePath == null) {
            (outputStream as ByteArrayOutputStream).toByteArray()
        } else
            null
    }

    public fun downloadLocally(fileId: String, downloadDir: String, numWorkers: Int = 8) {
        val fileSize = getSize(fileId)
        val fileName = getName(fileId)

        /* chunk size of 25 MB */
        val chunkSizeConst: Long = 25 * 2.0.pow(20).toLong()

        val chunkDir = Paths.get(downloadDir, fileId).toString()
        Files.createDirectories(Paths.get(chunkDir))

        val tasks = ArrayList<DownloadTask>()
        val chunks = ArrayList<String>()

        // loop on required chunks and create download tasks
        var firstBytePos: Long = 0
        while (firstBytePos < fileSize) {

            val lastBytePos: Long = min(firstBytePos + chunkSizeConst - 1, fileSize - 1)

            val chunkName = "chunk-$firstBytePos-$lastBytePos"
            val chunkPath = Paths.get(chunkDir, chunkName).toString()

            val oldChunk = File(chunkPath)
            val chunkSize = if (oldChunk.exists() && oldChunk.isFile)
                oldChunk.length()
            else {
                oldChunk.delete()
                0
            }
            val expectedChunkSize: Long = (lastBytePos - firstBytePos) + 1

            if (chunkSize != expectedChunkSize)
                tasks.add(
                    DownloadTask(
                        this,
                        fileId,
                        chunkPath,
                        firstBytePos,
                        lastBytePos
                    )
                )

            firstBytePos += expectedChunkSize
            chunks.add(chunkPath)
        }

        // actual downloading starts here for remaning chunks
        val executor = Executors.newFixedThreadPool(numWorkers)
        executor.invokeAll(tasks)
        executor.shutdown()

        val filePath = Paths.get(downloadDir, fileName).toString()
        val fileStream = FileOutputStream(filePath)
        chunks.forEach { chunkPath ->
            val inputStream = FileInputStream(chunkPath)
            fileStream.write(inputStream.readAllBytes())
        }

        if (File(filePath).length() == fileSize) {
            println("File downloaded, deleting chunks..")
            File(chunkDir).deleteRecursively()
        }

        println("Completed.")
    }

    public fun downloadAsInputStream(
        fileId: String,
        firstBytePos: Long? = null,
        lastBytePos: Long? = null
    ): InputStream {
        val request = service.files().get(fileId)
        request.mediaHttpDownloader.isDirectDownloadEnabled = false
        request.mediaHttpDownloader.chunkSize = CHUNK_SIZE.toInt()

        if (firstBytePos != null) {
            // **** Range headers are required here
            //this works
            request.requestHeaders.range = "bytes=$firstBytePos-${lastBytePos ?: ""}"
        }

        return request.executeMediaAsInputStream()
    }

    private fun uploadDirectory(
        directoryPath: Path,
        driveFolderParentId: String = "root",
        overwrite: Boolean
    ): String? {
        val index = HashMap<String, String>()

        val cache = HashMap<String, String>()
        Files.walk(directoryPath).filter { it.toFile().isFile }.forEach { filePath: Path ->

            val relativeFilePath = directoryPath.parent.relativize(filePath)
            val parentPaths: Stack<Path> = Stack()
            var parentPath = relativeFilePath.parent
            while (parentPath != null) {
                parentPaths.add(parentPath)
                parentPath = parentPath.parent
            }

            var tempDriveFolderParentId = driveFolderParentId
            while (!parentPaths.empty()) {
                parentPath = parentPaths.peek()

                val name = parentPath.fileName.toString()
                val key = "$name-$tempDriveFolderParentId"

                if (cache.containsKey(key))
                    tempDriveFolderParentId = cache[key]!!
                else {
                    println("Creating directory: $parentPath")
                    tempDriveFolderParentId = createDirectory(name, tempDriveFolderParentId, overwrite)
                    cache[key] = tempDriveFolderParentId
                }
                parentPaths.pop()
            }

            println("Uploading file: $filePath")
            val fileId = uploadFile(
                filePath = filePath,
                driveFolderParentId = tempDriveFolderParentId,
                overwrite = overwrite
            )

            index[filePath.toString()] = fileId
            index[filePath.parent.toString()] = tempDriveFolderParentId
        }

        return index[directoryPath.toString()]
    }

    private fun createDirectory(
        name: String,
        driveFolderParentId: String = "root",
        overwrite: Boolean
    ): String {
        var file = getFileByQuery(
            "name='${name}' and mimeType='application/vnd.google-apps.folder' " +
                    "and '${driveFolderParentId}' in parents and trashed=false"
        )

        if (overwrite && file != null) {

            println("Overwriting folder ${name}")

            /* Deletes without moving to trash */
            service.files().delete(file.id).execute()
            file = null
        }

        if (file == null) {
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = name
            fileMetadata.mimeType = "application/vnd.google-apps.folder"
            fileMetadata.parents = mutableListOf(driveFolderParentId)
            file = service.files().create(fileMetadata).setFields("id").execute()
        }

        return file!!.id
    }

    private fun uploadFile(
        filePath: Path,
        fileType: String = "",
        driveFolderParentId: String = "root",
        overwrite: Boolean
    ): String {
        val uploadFile = filePath.toFile()

        var file = getFileByQuery(
            "name = '${uploadFile.name}' and mimeType!='application/vnd.google-apps.folder' " +
                    "and '${driveFolderParentId}' in parents and trashed=false"
        )

        if (overwrite && file != null && file.getSize() == uploadFile.length()) {

            println("Overwriting file ${uploadFile.name}")

            /* Deletes without moving to trash */
            service.files().delete(file.id).execute()
            file = null
        }

        if (file == null) {
            val mediaContent = FileContent(fileType, uploadFile)
            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = uploadFile.name

            val parents = mutableListOf<String>()
            parents.add(driveFolderParentId)
            fileMetadata.parents = parents


            val request = service.files().create(fileMetadata, mediaContent)
            request.mediaHttpUploader.isDirectUploadEnabled = false
            request.mediaHttpUploader.setProgressListener {
                println("Upload progress for ${uploadFile.name} - ${it.progress * 100}%")
            }
            file = request.execute()
        }

        return file!!.id
    }

    public fun upload(path: Path, driveFolderParentId: String = "root", overwrite: String = "false") {
        val file = path.toFile()
        if (!file.exists())
            println("Path doesn't exist, aborting.")

        if (file.isDirectory)
            uploadDirectory(
                directoryPath = path.toRealPath(),
                driveFolderParentId = driveFolderParentId,
                overwrite = overwrite == "true"
            )
        else if (file.isFile)
            uploadFile(
                filePath = path.toRealPath(),
                driveFolderParentId = driveFolderParentId,
                overwrite = overwrite == "true"
            )
    }
}

