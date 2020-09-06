package com.vbb.android

import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import java.io.InputStream

import java.util.*
import kotlin.math.pow

class GDrive private constructor(private val service: Drive) {

    companion object {
        fun getGDrive(activity: Activity): GDrive? {
            val account = GoogleSignIn.getLastSignedInAccount(activity) ?: return null
            val credential = GoogleAccountCredential.usingOAuth2(
                activity,
                Collections.singleton(DriveScopes.DRIVE)
            )
            credential.selectedAccount = account.account
            val service =
                Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                    .setApplicationName(activity.resources.getString(R.string.app_name)).build()
            return GDrive(service)
        }
    }

    /* 32 MB */
    private val CHUNK_SIZE: Long = 32 * 2.0.pow(20).toLong()

    public fun getFile(fileId: String): File {
        return service.files().get(fileId).setFields("id, name, size, mimeType").execute()!!
    }

    public fun getFileByQuery(query: String): File? {
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

    public fun getFilesByQuery(query: String): ArrayList<File> {
        val files = ArrayList<File>()
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

    public fun downloadAsInputStream(
        fileId: String,
        firstBytePos: Long? = null,
        lastBytePos: Long? = null
    ): InputStream {
        val request = service.files().get(fileId)
        request.mediaHttpDownloader.isDirectDownloadEnabled = false
        request.mediaHttpDownloader.chunkSize = CHUNK_SIZE.toInt()

        if (firstBytePos != null) {
            // Range headers are required here
            // this works
            request.requestHeaders.range = "bytes=$firstBytePos-${lastBytePos ?: ""}"
        }

        return request.executeMediaAsInputStream()
    }
}
