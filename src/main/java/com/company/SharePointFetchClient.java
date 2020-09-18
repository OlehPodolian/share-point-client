package com.company;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.util.Map;

//https://www.rgagnon.com/javadetails/java-get-document-sharepoint-library.html
public class SharePointFetchClient {

    public static void main(String[] args) throws Exception {
        CloseableHttpClient httpclient = HttpClients.custom()
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0,false))
                .build();

        File file = new File("creds.json");
        if (! file.exists()) {
            throw new RuntimeException("You are unauthorized");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> props = objectMapper.readValue(file, new TypeReference<Map<String, String >>(){});


        String user = props.get("username");
        String pwd = props.get("password");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY,
                new NTCredentials(user, pwd, "", props.get("domain")));

        // You may get 401 if you go through a load-balancer.
        // To fix this, go directly to one the sharepoint web server or
        // change the config. See this article :
        // http://blog.crsw.com/2008/10/14/unauthorized-401-1-exception-calling-web-services-in-sharepoint/
        HttpHost target = new HttpHost(props.get("hostname"), 80, "http");
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        // The authentication is NTLM.
        // To trigger it, we send a minimal http request
        HttpHead request1 = new HttpHead("/");
        CloseableHttpResponse response1 = null;
        try {
            response1 = httpclient.execute(target, request1, context);
            EntityUtils.consume(response1.getEntity());
            System.out.println("1 : " + response1.getStatusLine().getStatusCode());
        }
        finally {
            if (response1 != null ) response1.close();
        }

        // The real request, reuse authentication
        String fileName = "/tr/ap_docs/Test/hi.txt";  // source
        String targetFolder = "/Users/olegpodolian/Desktop/";
        HttpGet request2 = new HttpGet("/_api/web/GetFileByServerRelativeUrl('" + fileName + "')/$value");
        CloseableHttpResponse response2 = null;
        try {
            response2 = httpclient.execute(target, request2, context);
            HttpEntity entity = response2.getEntity();
            int rc = response2.getStatusLine().getStatusCode();
            String reason = response2.getStatusLine().getReasonPhrase();
            if (rc == HttpStatus.SC_OK) {
                System.out.println("Writing "+ fileName + " to " + targetFolder);
                File f = new File(fileName);
                File ff= new File(targetFolder, f.getName());  // target
                // writing the byte array into a fileName using Apache Commons IO
                FileUtils.writeByteArrayToFile(ff, EntityUtils.toByteArray(entity));
            }
            else {
                throw new Exception("Problem while receiving " + fileName + "  reason : "
                        + reason + " httpcode : " + rc);
            }
        }
        finally {
            if (response2 != null) response2.close();
        }
        System.out.println("Over");;

    }
}
