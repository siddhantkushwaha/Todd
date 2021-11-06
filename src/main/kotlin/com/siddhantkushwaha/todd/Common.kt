package com.siddhantkushwaha.todd

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.auth.oauth2.UserCredentials
import java.io.InputStreamReader
import java.nio.file.Path

object Common {

    private const val credentialPath = "/credentials.json"

    private fun getClientSecrets(jsonFactory: JacksonFactory): GoogleClientSecrets {
        // Load client secrets.
        val inputStream = GPhotos::class.java.getResourceAsStream(credentialPath)
            ?: throw Exception("Resource not found: $credentialPath")

        return GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))
    }

    private fun getCredential(
        httpTransport: NetHttpTransport,
        jsonFactory: JacksonFactory,
        clientSecrets: GoogleClientSecrets,
        scopes: List<String>,
        tokensPath: Path
    ): Credential {
        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow
            .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(FileDataStoreFactory(tokensPath.toFile()))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    public fun getCredential(
        httpTransport: NetHttpTransport,
        jsonFactory: JacksonFactory,
        scopes: List<String>,
        tokensPath: Path
    ): Credential {
        val clientSecrets = getClientSecrets(jsonFactory)
        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow
            .Builder(httpTransport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(FileDataStoreFactory(tokensPath.toFile()))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    public fun getCredentials(
        httpTransport: NetHttpTransport,
        jsonFactory: JacksonFactory,
        scopes: List<String>,
        tokensPath: Path
    ): UserCredentials {
        val clientSecrets = getClientSecrets(jsonFactory)
        val credential = getCredential(httpTransport, jsonFactory, scopes, tokensPath)
        return UserCredentials.newBuilder()
            .setClientId(clientSecrets.details.clientId)
            .setClientSecret(clientSecrets.details.clientSecret)
            .setRefreshToken(credential.refreshToken)
            .build()
    }
}