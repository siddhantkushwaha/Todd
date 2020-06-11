###Todd is a utility library to interact with Google Drive Files.

###Features - 
 1. Download files functionality with resume capability.
 2. Upload files and even folders without zipping.
 3. Files on drive provided as InputStream to creating streaming services.

###How to install?

Update your build.gradle file.

    repositories {  
        maven { url 'https://jitpack.io' }  
    }
    
    implementation 'com.github.siddhantkushwaha:todd:version'

###This is how I use it to stream content over http.



    package com.siddhantkushwaha.server;
    
    import com.siddhantkushwaha.todd.GDrive;
    import org.springframework.http.*;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.PathVariable;
    import org.springframework.web.bind.annotation.RequestHeader;
    import org.springframework.web.bind.annotation.RestController;
    import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
    
    import java.io.*;
    import java.util.List;
    
    @RestController
    public class GDriveController {

	    private static final GDrive gDrive = new GDrive();

	    @GetMapping("/file/{fileId}")
	    public ResponseEntity<StreamingResponseBody> func(@PathVariable String fileId, @RequestHeader() HttpHeaders requestHeaders) throws FileNotFoundException {

	        long size = gDrive.getSize(fileId);
	        System.out.printf("********************* Total Content Length - %s *********************", size);

	        List<HttpRange> ranges = requestHeaders.getRange();
	        long start = 0, end = size - 1;
	        if (!ranges.isEmpty()) {
	            start = ranges.get(0).getRangeStart(size);
	            end = ranges.get(0).getRangeEnd(size);
	        }

	        System.out.printf("********************* Range: bytes=%s-%s *********************", start, end);

	        InputStream inputStream = gDrive.downloadAsInputStream(fileId, start, end);

	        /*
	            // ***** writeFile writes data from inputSteam to outStream only when requested by client ******
                // ***** also make content format supports streaming with partial content ******
	            fun writeFile(ist: InputStream, fos: OutputStream) {
	                // write in 2MB chunks, this can be changed
	                val buffer = ByteArray(1024 * 1024 * 2)
	                var bufferLength = ist.read(buffer)
	                while (bufferLength > 0) {
	                    println("Writing..")
	                    fos.write(buffer, 0, bufferLength)
	                    fos.flush()
	                    bufferLength = ist.read(buffer)
	                }
	            }
	        */
	        StreamingResponseBody srb = outputStream -> Util.Companion.writeFile(inputStream, outputStream);

	        HttpHeaders httpHeaders = new HttpHeaders();
	        long contentLength = (end - start) + 1;
	        httpHeaders.setContentLength(contentLength);
	        httpHeaders.set("Content-Range", String.format("bytes %d-%d/%d", start, end, size));

	        return new ResponseEntity<>(srb, httpHeaders, HttpStatus.PARTIAL_CONTENT);
	    }
    }

[Also available here.](https://gist.github.com/siddhantkushwaha/ba973430d61ffcf5fa7d9d19471d9675)

##Doc coming soon.