    dependencies {  
	  ...
      
      implementation 'com.google.http-client:google-http-client-gson:1.26.0'  
      
      implementation 'com.google.android.gms:play-services-auth:18.1.0'  
      implementation 'com.google.api-client:google-api-client:1.23.0'  
      implementation 'com.google.api-client:google-api-client-android:1.22.0'  
      
      implementation "com.google.apis:google-api-services-drive:v3-rev110-1.23.0"  
    }
Other than this, enabling OAuth ClientId needs to be enabled for Android. OAuth consent screen is also required.
