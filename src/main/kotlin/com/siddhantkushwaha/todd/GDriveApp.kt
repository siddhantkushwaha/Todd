package com.siddhantkushwaha.todd

import java.nio.file.Paths

class GDriveApp {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val gDrive = GDrive()

            if (args.isEmpty())
                throw Exception("Command not found.")

            when (args[0]) {
                "download" -> {
                    gDrive.download(
                            id = args.getOrNull(1)!!,
                            downloadDir = Paths.get(args.getOrNull(2) ?: "downloads"),
                            numWorkers = Integer.parseInt(args.getOrNull(3) ?: "8")
                    )
                }

                "upload" -> {
                    gDrive.upload(
                            path = Paths.get(args.getOrNull(1)!!),
                            driveFolderParentId = args.getOrNull(2) ?: "root",
                            overwrite = args.getOrNull(3) ?: "false"
                    )
                }
            }
        }
    }
}





