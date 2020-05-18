package com.siddhantkushwaha.todd.gdrive

import java.nio.file.Paths

class GDriveApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty())
                throw Exception("Command not found.")

            val gDrive = GDrive()
            when (args[0]) {
                "download" -> {
                    gDrive.downloadLocally(
                        fileId = args.getOrNull(1)!!,
                        downloadDir = args.getOrNull(2) ?: "downloads",
                        numWorkers = Integer.parseInt(args.getOrNull(3) ?: "8")
                    )
                }

                "upload" -> {
                    gDrive.upload(
                        path = Paths.get(args.getOrNull(1)!!),
                        driveFolderParentId = args.getOrNull(2)
                    )
                }
            }
        }
    }
}



