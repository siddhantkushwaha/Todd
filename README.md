### Todd is a utility library to interact with Google Drive Files.

### Features -

1.  Download files functionality with resume capability.
2.  Upload files and even folders without zipping.
3.  Files on drive provided as InputStream to creating streaming services.

### How to install?

Update your build.gradle file.

```kotlin
repositories {
    maven { url 'https://jitpack.io' }
}

implementation 'com.github.siddhantkushwaha:todd:version'
```

### Steps to generate credentials.json

You can use a many steps manual option, or the 6 steps **'Quickstart'** workaround.

**Manual/customized:**

1. Go to https://console.cloud.google.com/projectcreate

2. Fill in Project Name, like "todd-Testing" or so, lease Location unchanged

3. Change Project ID (optional)

4. Click "CREATE"

5. Wait a couple of seconds until the project is created and open it (click "VIEW")

6. On the APIs pane, click "Go to APIs overview"

7. Click "ENABLE APIS AND SERVICES"

8. Enter "Drive", select "Google Drive API"

9. Click "ENABLE"

10. Go to "Credentials" menu in the left menu bar

11. Click "CONFIGURE CONSENT SCREEN"

12. Choose "External", click "CREATE"

13. Fill in something like "todd" in the "Application name" box

14. At the bottom click "Save"

15. Go to "Credentials" menu in the left menu bar (again)

16. Click **"CREATE CREDENTIALS"**

17. Select **"OAuth client ID"**

18. Select **"Desktop app"** as **"Application type"**

19. Change the name (optional)

20. Click "Create"

21. Click "OK" in the "OAuth client created" dialog

22. In the **"OAuth 2.0 Client IDs"** section click on the **just create Desktop app** line.

23. In the top bar, click "DOWNLOAD JSON"

24. You will get a file like "client_secret_xxxxxx.apps.googleusercontent.com.json", rename it to "credentials.json" and copy this in `src/main/resources` directory.



## Alternative method (easier):

This will 'abuse' a 'Quickstart' project.

1. Go to https://developers.google.com/drive/api/v3/quickstart/python

2. Click the "Enabled the Drive API"

3. "Desktop app" will already be selected on the "Configure your OAuth client" dialog

4. Click "Create"

5. Click "DOWNLOAD CLIENT CONFIGURATION"

6. You will get a file like "credentials.json", and put this in root of the repo.

 

On the first use, you will get a browser screen that you need to grant access for it, and because we haven't granted out OAuth consent screen (This app isn't verified), we get an extra warning. You can use the "Advanced" link, and use the "Go to yourappname (unsafe)" link.

##### Insert credentials.json into resources directory

should look like this -

```json
{
  "installed": {
    "client_id": "********.apps.googleusercontent.com",
    "project_id": "******",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
    "client_secret": "*********",
    "redirect_uris": ["urn:ietf:wg:oauth:2.0:oob", "http://localhost"]
  }
}
```

### This is how I use it to stream content over http.

```kotlin
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
            // ***** make sure content supports streaming with partial data ******
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
```

[Also available here.](https://gist.github.com/siddhantkushwaha/ba973430d61ffcf5fa7d9d19471d9675)

## Doc coming soon.
