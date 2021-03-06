package com.testdroid.api;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.testdroid.api.http.MultipartFormDataContent;
import com.testdroid.api.model.APIDevice;
import com.testdroid.api.model.APIUser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

/**
 *
 * @author kajdus
 */
public class DefaultAPIClient implements APIClient {

    protected static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    protected static Credential getCredential() { return new Credential.Builder(BearerToken.queryParameterAccessMethod()).build(); }
    protected static HttpRequestFactory getRequestFactory(String accessToken) {
        final Credential credential = getCredential();
        credential.setAccessToken(accessToken);
        return HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {

            @Override
            public void initialize(HttpRequest request) throws IOException {
                credential.initialize(request);
            }
        });
    }
    protected static String API_URI = "/api/v2";
    
    public final static int HTTP_CONNECT_TIMEOUT = 60000;
    public final static int HTTP_READ_TIMEOUT = 60000;
    
    protected String cloudURL;
    protected String apiURL;
    protected String username;
    protected String password;
    protected String accessToken;
    protected String refreshToken;
    protected long accessTokenExpireTime = 0;

    public DefaultAPIClient(String cloudURL, String username, String password) {
        this.cloudURL = cloudURL;
        this.apiURL = cloudURL + API_URI;
        this.username = username;
        this.password = password;
    }
    
    protected String getAccessToken() {
        if(accessToken == null) {
            try {
                accessToken = acquireAccessToken();
            }
            catch(APIException ex) {
                ex.printStackTrace();
                // Do nothing, leave null
            }
        }
        else if(System.currentTimeMillis() > (accessTokenExpireTime-10*1000) ) {
            try {
                accessToken = refreshAccessToken();
            }
            catch(APIException ex) {
                ex.printStackTrace();
                accessToken = null; // if refreshing failed, then we are not authorized                
            }
        }
        return accessToken;
    }
    
    protected String acquireAccessToken() throws APIException {
        try {
            HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(new GenericUrl(
                    String.format("%s/oauth/token?client_id=testdroid-cloud-api&grant_type=password&username=%s&password=%s",
                    cloudURL, username, password)));
            request.setConnectTimeout(HTTP_CONNECT_TIMEOUT); // one minute
            request.setReadTimeout(HTTP_READ_TIMEOUT); // one minute
            HttpResponse response = request.execute();
            if(response.getStatusCode() != 200) {
                throw new APIException(response.getStatusCode(), "Failed to acquire access token");
            }
            String content = StringUtils.join(IOUtils.readLines(response.getContent()), "\n");            
            JSONObject json = JSONObject.fromObject(content);
            accessTokenExpireTime = System.currentTimeMillis()+(Long.parseLong(json.optString("expires_in"))*1000);
            refreshToken = json.optString("refresh_token");
            return json.optString("access_token");
        }
        catch(IOException ex) {
            throw new APIException("Failed to acquire access token", ex);
        }
    }
    
    protected String refreshAccessToken() throws APIException {
        try {
            if(refreshToken == null) {
                return null;
            }
            HttpRequest request = HTTP_TRANSPORT.createRequestFactory().buildGetRequest(new GenericUrl(
                    String.format("%s/oauth/token?client_id=testdroid-cloud-api&grant_type=refresh_token&refresh_token=%s",
                    cloudURL, refreshToken)));
            request.setConnectTimeout(HTTP_CONNECT_TIMEOUT); // one minute
            request.setReadTimeout(HTTP_READ_TIMEOUT); // one minute
            HttpResponse response = request.execute();
            if(response.getStatusCode() != 200) {
                throw new APIException(response.getStatusCode(), "Failed to acquire access token");
            }
            String content = StringUtils.join(IOUtils.readLines(response.getContent()), "\n");            
            JSONObject json = JSONObject.fromObject(content);
            accessTokenExpireTime = System.currentTimeMillis()+(Long.parseLong(json.optString("expires_in"))*1000);
            refreshToken = json.optString("refresh_token");
            return json.optString("access_token");
        }
        catch(IOException ex) {
            throw new APIException("Failed to acquire access token", ex);
        }
    }
    
    @Override
    public <T extends APIEntity> T get(String uri, Class<T> type) throws APIException {
        try {
            return getOnce(uri, type);
        }
        catch(APIException ex) {
            if(ex.getStatus() != null && HttpStatus.SC_UNAUTHORIZED == ex.getStatus()) {
                // Access token may have expired. Clean and try again.
                accessToken = null;
                return getOnce(uri, type);
            }
            else {
                throw ex;
            }
        }
    }
    
    @Override
    public InputStream get(String uri) throws APIException {
        try {
            return getStream(uri);
        }
        catch(APIException ex) {
            if(ex.getStatus() != null && HttpStatus.SC_UNAUTHORIZED == ex.getStatus()) {
                // Access token may have expired. Clean and try again.
                accessToken = null;
                return getStream(uri);
            }
            else {
                throw ex;
            }
        }        
    }
    
    /**
     * Tries to call API once. Returns expected entity or throws exception.
     */
    private <T extends APIEntity> T getOnce(String uri, Class<T> type) throws APIException {
        // Build request
        HttpRequestFactory factory = getRequestFactory(getAccessToken());
        HttpRequest request;
        HttpResponse response;
        try {
            // Call request and parse result
            JAXBContext context = JAXBContext.newInstance(type);
            
             request = factory.buildGetRequest(new GenericUrl(apiURL + uri));
             request.setHeaders(new HttpHeaders().setAccept("application/xml"));

            response = request.execute();
            if(!Arrays.asList(HttpStatus.SC_OK, HttpStatus.SC_ACCEPTED, HttpStatus.SC_CREATED, HttpStatus.SC_NO_CONTENT).contains(response.getStatusCode())) {
                throw new APIException(response.getStatusCode(), String.format("Failed to execute api call: %s", uri));
            }
            Unmarshaller unmarshaller = context.createUnmarshaller();
            T result = (T) unmarshaller.unmarshal(response.getContent());
            result.client = this;
            result.selfURI = uri;
            return result;
        }
        catch(JAXBException ex) {
            throw new APIException(String.format("Failed to parse response as %s", type.getName()), ex);
        }
        catch(IOException ex) {
            throw new APIException(String.format("Failed to execute API call: %s", uri), ex);
        }
    }
    
    private InputStream getStream(String uri) throws APIException {
        HttpRequestFactory factory = getRequestFactory(getAccessToken());
        HttpRequest request;
        HttpResponse response;
        try {
             request = factory.buildGetRequest(new GenericUrl(apiURL + uri));

            response = request.execute();
            if(!Arrays.asList(HttpStatus.SC_OK, HttpStatus.SC_ACCEPTED, HttpStatus.SC_CREATED).contains(response.getStatusCode())) {
                throw new APIException(response.getStatusCode(), String.format("Failed to execute api call: %s", uri));
            }

            return response.getContent();
        }
        catch(IOException ex) {
            throw new APIException(String.format("Failed to execute API call: %s", uri), ex);
        }
    }

    @Override
    public <T extends APIEntity> T post(String uri, Object body, Class<T> type) throws APIException {
        try {
            return postOnce(uri, body, null, type);
        }
        catch(APIException ex) {
            if(ex.getStatus() != null && HttpStatus.SC_UNAUTHORIZED == ex.getStatus()) {
                // Access token may have expired. Clean and try again.
                accessToken = null;
                return postOnce(uri, body, null, type);
            }
            else {
                throw ex;
            }
        }
    }

    protected <T extends APIEntity> T postOnce(String uri, Object body, String contentType, Class<T> type) throws APIException {
        if(contentType == null) {
            contentType = "application/xml";
        }
        HttpRequestFactory factory = getRequestFactory(getAccessToken());
        HttpRequest request;
        HttpResponse response = null;
        try {
            HttpContent content;
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept("application/xml");
            if (body instanceof File) {

                MultipartFormDataContent multipartContent = new MultipartFormDataContent();
                FileContent fileContent = new FileContent("file", (File)body);

                MultipartFormDataContent.Part filePart = new MultipartFormDataContent.Part("file", fileContent) ;
                multipartContent.addPart(filePart);

                content = multipartContent;

            } else if (body instanceof InputStream) {
                headers.setContentType(contentType);
                content = new InputStreamContent(contentType, (InputStream) body);
            } else if (body instanceof APIEntity) {
                content = new InputStreamContent(contentType, IOUtils.toInputStream(((APIEntity)body).toXML()));
            } else if (body == null) {
                content = null;
            } else {
                content = new UrlEncodedContent(body);
            }
            request = factory.buildPostRequest(new GenericUrl(apiURL + uri), content );
            request.setHeaders(headers);

            // Call request and parse result
            JAXBContext context = JAXBContext.newInstance(type);

            Unmarshaller unmarshaller = context.createUnmarshaller();
            response = request.execute();
            if(response == null) {
                throw new APIException("No response from API");
            }
            if(response.getStatusCode() < HttpStatus.SC_OK || response.getStatusCode() >= 300 ) {
                throw new APIException(response.getStatusCode(), "Failed to post resource: " + response.getStatusMessage());
            }
            T result = (T) unmarshaller.unmarshal(response.getContent());
            result.client = this;
            result.selfURI = uri;
            // In case of entity creation, we need to update its url
            if(response.getStatusCode() == HttpStatus.SC_CREATED && result.hasId()) {
                result.selfURI += String.format("/%s", result.getId());
            }
            return result;
        }
        catch(JAXBException ex) {
            throw new APIException(response != null ? response.getStatusCode() : null, String.format("Failed to parse response as %s", type.getName()), ex);
        }
        catch(IOException ex) {
            throw new APIException(String.format("Failed to execute API call: %s", uri), ex);
        }
    }
    
    @Override
    public <T extends APIEntity> T postFile(String uri, String contentType, File file, Class<T> type) throws APIException {
        try {
            return postOnce(uri, file, contentType, type);
        }
        catch(APIException ex) {
            if(ex.getStatus() != null && HttpStatus.SC_UNAUTHORIZED == ex.getStatus()) {
                // Access token may have expired. Clean and try again.
                accessToken = null;
                return postOnce(uri, file, contentType, type);
            }
            else {
                throw ex;
            }
        }
    }
    
    @Override
    public void delete(String uri) throws APIException {
        try {
            deleteOnce(uri);
        }
        catch(APIException ex) {
            if(ex.getStatus() != null && HttpStatus.SC_UNAUTHORIZED == ex.getStatus()) {
                // Access token may have expired. Clean and try again.
                accessToken = null;
                deleteOnce(uri);
            }
            else {
                throw ex;
            }
        }
    }
    
    private void deleteOnce(String uri) throws APIException {
        HttpRequestFactory factory = getRequestFactory(getAccessToken());
        HttpRequest request;
        HttpResponse response;
        try {
             request = factory.buildDeleteRequest(new GenericUrl(apiURL + uri));
             request.setHeaders(new HttpHeaders().setAccept("application/xml"));

            response = request.execute();
            if(response == null) {
                throw new APIException("No response from API");
            }
            if(response.getStatusCode() != HttpStatus.SC_OK) {
                throw new APIException(response.getStatusCode(), "Failed to delete resource: " + response.getStatusMessage());
            }
        }
        catch(IOException ex) {
            throw new APIException(String.format("Failed to execute API call: %s", uri), ex);
        }
    }

    @Override
    public APIUser me() throws APIException {
        return get("/me", APIUser.class);
    }

    @Override
    public APIUser register(String email) throws APIException {
        APIUser result = post("/users", String.format("email=%s", APIEntity.encodeURL(email)), APIUser.class);
        result.selfURI = "/me";
        return result;
    }

    
    private static String getDevicesURI(APIDevice.Filter... filters) {
        return filters.length > 0 ? String.format("/devices?filter=%s", StringUtils.join(filters, "&filter=")) : "/devices";
    }
    
    @Override
    public APIListResource<APIDevice> getDevices(APIDevice.Filter... filters) throws APIException {
        return new APIListResource<APIDevice>(this, getDevicesURI(filters), APIDevice.class);
    }

    @Override
    public APIListResource<APIDevice> getDevices(long offset, long limit, String search, APISort sort, APIDevice.Filter... filters) throws APIException {
        return new APIListResource<APIDevice>(this, getDevicesURI(filters), offset, limit, search, sort, APIDevice.class);
    }

}


