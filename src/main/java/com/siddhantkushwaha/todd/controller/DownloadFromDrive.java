package com.siddhantkushwaha.todd.controller;

import com.siddhantkushwaha.todd.gdrive.GDrive;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
class DownloadFromDrive {
    @RequestMapping(value = "/download/drive", method = RequestMethod.GET)
    byte[] download(
            @RequestParam("fileId") String fileId,
            @RequestParam(value = "startByte", required = false) Long startByte,
            @RequestParam(value = "endByte", required = false) Long endByte) {

        System.out.printf("Request received for fileId %s, startByte %d, endByte: %d", fileId, startByte, endByte);

        GDrive gdrive = new GDrive();
        if (startByte != null && endByte != null && startByte <= endByte)
            return gdrive.download(fileId, null, startByte, endByte);
        else
            return gdrive.download(fileId, null, null, null);
    }
}