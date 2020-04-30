package com.siddhantkushwaha.todd.gdrive


fun main(args: Array<String>) {

    if (args.size < 2)
        throw Exception("Invalid arguments.")
    val downloadDir = args[0]
    val link = args[1]

    val gDrive = GDrive()

    gDrive.downloadLocally(link, downloadDir, numWorkers = 8)

    /*gDrive.downloadLocally(
            "https://drive.google.com/open?id=1yyHmZzM9MCwITOHSih3GmP8C4-Ex6ojJ",
            downloadDir = "Downloads/Music", numWorkers = 8
    )*/
}

