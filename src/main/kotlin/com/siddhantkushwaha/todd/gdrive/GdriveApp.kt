package com.siddhantkushwaha.todd.gdrive


fun main(args: Array<String>) {
    if (args.isEmpty())
        throw Exception("Command not found.")

    val gDrive = GDrive()
    when (args[0]) {
        "download" -> {
            if (args.size < 3)
                throw Exception("Less arguments than expected.")
            gDrive.downloadLocally(fileId = args[1], downloadDir = args[2], numWorkers = 8)
        }

        "upload" -> {
            if (args.size < 4)
                throw Exception("Less arguments than expected.")
            gDrive.upload(filePath = args[1], fileType = args[2], parentId = args[3])
        }
    }
}

