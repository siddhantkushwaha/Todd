package com.siddhantkushwaha.todd.gdrive

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import org.springframework.util.FileSystemUtils
import org.springframework.web.util.UriComponentsBuilder

import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.pow


class GDrive {

    private val APPLICATION_NAME = "GDrive - Todd"
    private val SCOPES = listOf(DriveScopes.DRIVE)
    private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
    private val CREDENTIALS_FILE_PATH = "config_drive.json"
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

        val inputStream = FileInputStream(CREDENTIALS_FILE_PATH)
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    public fun getIdFromLink(link: String): String? {
        val queryParams = UriComponentsBuilder.fromUriString(link).build().queryParams
        return queryParams["id"]?.get(0)
    }

    public fun download(
            fileId: String,
            filePath: String? = null,
            firstBytePos: Long? = null,
            lastBytePos: Long? = null
    ): ByteArray? {
        val request = service.files().get(fileId)
        request.mediaHttpDownloader
                .setDirectDownloadEnabled(true)
                .setChunkSize(CHUNK_SIZE.toInt())

        if (firstBytePos != null && lastBytePos != null)
            request.mediaHttpDownloader.setContentRange(firstBytePos, lastBytePos.toInt())

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


    public fun downloadLocally(link: String, downloadDir: String, numWorkers: Int = 8) {
        val fileId = getIdFromLink(link)!!
        val file = service.files().get(fileId).setFields("name, size").execute()!!

        /* chunk size of 25 MB */
        val chunkSizeConst: Long = 25 * 2.0.pow(20).toLong()
        val chunkDir = Paths.get(downloadDir, fileId).toString()
        Files.createDirectories(Paths.get(chunkDir))


        val executor = Executors.newFixedThreadPool(numWorkers)
        val chunks = ArrayList<String>()

        var firstBytePos: Long = 0
        while (firstBytePos < file.getSize()) {

            val lastBytePos: Long = min(firstBytePos + chunkSizeConst - 1, file.getSize() - 1)

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
                executor.execute {
                    download(fileId, chunkPath, firstBytePos, lastBytePos)
                    println("Downloaded $chunkName for file ${file.name}.")
                }

            firstBytePos += expectedChunkSize
            chunks.add(chunkPath)
        }

        executor.shutdown()

        val filePath = Paths.get(downloadDir, file.name).toString()
        val fileStream = FileOutputStream(filePath)
        chunks.forEach { chunkPath ->
            val inputStream = FileInputStream(chunkPath)
            fileStream.write(inputStream.readAllBytes())
        }

        if (File(filePath).length() == file.getSize()) {
            println("File downloaded, deleting chunks..")
            FileSystemUtils.deleteRecursively(File(chunkDir))
        }

        println("Completed.")
    }
}

