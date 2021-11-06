package com.siddhantkushwaha.todd

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.UserCredentials
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import java.nio.file.Paths


class GPhotos {
    private val scopes = listOf(
        "https://www.googleapis.com/auth/photoslibrary.readonly",
        "https://www.googleapis.com/auth/photoslibrary.appendonly"
    )
    private val tokenDirectoryPath = Paths.get("tokens/gphotos")

    private val client: PhotosLibraryClient
    private val settings: PhotosLibrarySettings

    init {
        val credentialsProvider = FixedCredentialsProvider.create(getCredentials())
        settings = PhotosLibrarySettings
            .newBuilder()
            .setCredentialsProvider(credentialsProvider)
            .build()

        client = PhotosLibraryClient.initialize(settings)
    }

    private fun getCredentials(): UserCredentials {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        return Common.getCredentials(
            httpTransport,
            JacksonFactory.getDefaultInstance(),
            scopes,
            tokenDirectoryPath
        )
    }
}


fun main() {
    GPhotos()
}