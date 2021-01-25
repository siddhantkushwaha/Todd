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
import com.google.api.services.drive.model.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.pow


class GDrive {

    private val applicationName = "GDrive - Todd"
    private val scopes = listOf(DriveScopes.DRIVE)
    private val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
    private val credentialPath = "/credentials.json"
    private val tokenDirectoryPath = Paths.get("tokens")

    /* 10 MB */
    private val chunkSize: Long = 10 * 2.0.pow(20).toLong()

    private val service: Drive

    init {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        service = Drive.Builder(httpTransport, jsonFactory, getCredentials(httpTransport))
            .setApplicationName(applicationName)
            .build()
    }

    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        // Load client secrets.

        val inputStream = GDrive::class.java.getResourceAsStream(credentialPath)
            ?: throw Exception("Resource not found: $credentialPath")

        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, jsonFactory, clientSecrets, scopes
        )
            .setDataStoreFactory(FileDataStoreFactory(tokenDirectoryPath.toFile()))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private fun formQuery(query: String): Drive.Files.List {
        return service.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("nextPageToken, files(id, name, size, mimeType)")
    }

    public fun getService(): Drive {
        return service
    }

    public fun getFile(fileId: String): File {
        return service.files().get(fileId).setFields("id, name, size, mimeType").execute()
            ?: throw Exception("File not found $fileId")
    }

    public fun getFileByQuery(query: String): File? {
        return formQuery(query).execute().files.firstOrNull()
    }

    public fun getFilesByQuery(query: String): HashSet<File> {
        val files = HashSet<File>()
        var pageToken: String? = null
        do {
            val result = formQuery(query).setPageToken(pageToken).execute()
            files.addAll(result.files)
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    public fun downloadFileAsInputStream(
        fileId: String,
        firstBytePos: Long? = null,
        lastBytePos: Long? = null
    ): InputStream {

        val request = service.files().get(fileId)
        request.mediaHttpDownloader.isDirectDownloadEnabled = false
        request.mediaHttpDownloader.chunkSize = chunkSize.toInt()

        if (firstBytePos != null) {
            // **** Range headers are required here
            //this works
            request.requestHeaders.range = "bytes=$firstBytePos-${lastBytePos ?: ""}"
        }

        return request.executeMediaAsInputStream()
    }

    public fun downloadFile(fileId: String, downloadDir: Path, overwrite: Boolean = false): Int {

        val driveFile = getFile(fileId)
        val driveFileSize = driveFile.getSize()
        val filePath = Paths.get(downloadDir.toString(), driveFile.name)

        val file = filePath.toFile()
        val diskFileSize = file.length()

        val startPos = if (file.exists() && !overwrite) diskFileSize else 0
        val endPos = driveFileSize - 1

        if (startPos > endPos) {
            println("Skipping, already downloaded. $filePath - $fileId")
            return 0
        }

        val fileOS = FileOutputStream(file, !overwrite)
        val driveFileIS = downloadFileAsInputStream(fileId, startPos, endPos)

        val buffer = ByteArray(2 * 1024 * 10124)
        var bufferLen: Int

        var downloadedBytes = startPos
        do {
            bufferLen = driveFileIS.read(buffer)
            if (bufferLen > 0) fileOS.write(buffer, 0, bufferLen)
            downloadedBytes += bufferLen

            println("Download progress for $filePath - $fileId: ${100 * (downloadedBytes / driveFileSize.toFloat())}%")

        } while (bufferLen > 0)

        fileOS.close()
        driveFileIS.close()

        return 0
    }

    public fun downloadFolder(id: String, downloadDir: Path, overwrite: Boolean = false): Int {
        val folder = getFile(id)
        if (folder.mimeType != "application/vnd.google-apps.folder") {
            return 1
        }

        val currentDirPath = Paths.get(downloadDir.toString(), folder.name)
        if (overwrite) {
            currentDirPath.toFile().deleteRecursively()
        }
        Files.createDirectories(currentDirPath)

        var retCode = 0
        for (file in getFilesByQuery("'${id}' in parents and trashed=false")) {
            retCode = if (file.mimeType == "application/vnd.google-apps.folder")
                downloadFolder(file.id, currentDirPath)
            else
                downloadFile(file.id, currentDirPath)

            if (retCode != 0)
                break
        }

        return retCode
    }

    public fun download(id: String, downloadDir: Path, overwrite: Boolean = false) {
        Files.createDirectories(downloadDir.toAbsolutePath())
        val file = getFile(id)

        val retCode = if (file.mimeType == "application/vnd.google-apps.folder")
            downloadFolder(id, downloadDir.toAbsolutePath(), overwrite)
        else
            downloadFile(id, downloadDir.toAbsolutePath(), overwrite)

        val out =
            if (retCode == 0)
                "Download successful."
            else
                "Download failed."
        println(out)
    }

    public fun uploadDirectory(directoryPath: Path, driveFolderParentId: String = "root", overwrite: Boolean): String? {

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
                        ?: throw Exception("Folder creation failed.")
                    cache[key] = tempDriveFolderParentId
                }
                parentPaths.pop()
            }

            println("Uploading file: $filePath")
            val fileId = uploadFile(
                filePath = filePath,
                driveFolderParentId = tempDriveFolderParentId,
                overwrite = overwrite
            ) ?: throw Exception("File upload failed.")

            index[filePath.toString()] = fileId
            index[filePath.parent.toString()] = tempDriveFolderParentId
        }

        return index[directoryPath.toString()]
    }

    public fun createDirectory(name: String, driveFolderParentId: String = "root", overwrite: Boolean): String? {

        var file = getFileByQuery(
            "name='${name}' and mimeType='application/vnd.google-apps.folder' " +
                    "and '${driveFolderParentId}' in parents and trashed=false"
        )

        if (overwrite && file != null) {

            println("Overwriting folder $name")

            /* Deletes without moving to trash */
            service.files().delete(file.id).execute()
            file = null
        }

        if (file == null) {
            val fileMetadata = File()
            fileMetadata.name = name
            fileMetadata.mimeType = "application/vnd.google-apps.folder"
            fileMetadata.parents = mutableListOf(driveFolderParentId)
            file = service.files().create(fileMetadata).setFields("id").execute()
        }

        return file?.id
    }

    public fun uploadFile(
        filePath: Path,
        fileType: String = "",
        driveFolderParentId: String = "root",
        overwrite: Boolean
    ): String? {

        val uploadFile = filePath.toFile()

        var file = getFileByQuery(
            "name = '${uploadFile.name}' and mimeType!='application/vnd.google-apps.folder' " +
                    "and '${driveFolderParentId}' in parents and trashed=false"
        )

        if (overwrite && file != null) {

            println("Overwriting file ${uploadFile.name}")

            /* Deletes without moving to trash */
            service.files().delete(file.id).execute()
            file = null
        }

        if (file == null) {
            val mediaContent = FileContent(fileType, uploadFile)
            val fileMetadata = File()
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

        return file?.id
    }

    public fun upload(path: Path, driveFolderParentId: String = "root", overwrite: Boolean = false) {
        val file = path.toFile()
        if (!file.exists())
            println("Path doesn't exist, aborting.")

        val out =
            if (file.isDirectory)
                uploadDirectory(
                    directoryPath = path.toAbsolutePath(),
                    driveFolderParentId = driveFolderParentId,
                    overwrite = overwrite
                )
            else
                uploadFile(
                    filePath = path.toAbsolutePath(),
                    driveFolderParentId = driveFolderParentId,
                    overwrite = overwrite
                )
        println("Uploaded: $out")
    }
}

