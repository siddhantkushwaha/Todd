package com.siddhantkushwaha.todd.gdrive


fun main(args: Array<String>) {

    if (args.size < 2)
        throw Exception("Invalid arguments.")

    val downloadDir = args[0]
    val link = args[1]

    val gdrive = GDrive()
    gdrive.downloadLocally(link, downloadDir, numWorkers = 8)
}

