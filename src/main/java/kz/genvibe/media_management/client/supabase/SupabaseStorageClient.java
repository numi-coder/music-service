package kz.genvibe.media_management.client.supabase;

import kz.genvibe.media_management.config.props.IntegrationProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal client for the Supabase Storage REST API.
 * Uploads use the project's secret key, which must stay on the server.
 */
@Component
@Slf4j
public class SupabaseStorageClient {

    private final String projectUrl;
    private final RestClient restClient;

    public SupabaseStorageClient(IntegrationProps integrationProps, RestClient.Builder builder) {
        var props = integrationProps.getSupabase();

        if (props == null || !props.hasCredentials()) {
            this.projectUrl = null;
            this.restClient = null;
            log.info("Supabase storage is not configured, audio files will be stored on local disk");
            return;
        }

        this.projectUrl = stripTrailingSlash(props.url());

        var clientBuilder = builder
            .baseUrl(projectUrl + "/storage/v1")
            .defaultHeader("apikey", props.secretKey());

        // Legacy service_role keys are JWTs and also go in the Authorization header.
        // New sb_secret_ keys are sent only as apikey.
        if (props.secretKey().startsWith("eyJ")) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.secretKey());
        }

        this.restClient = clientBuilder.build();
    }

    public boolean isEnabled() {
        return restClient != null;
    }

    /**
     * Uploads a file to a bucket and returns its public URL.
     * objectName must be a flat file name without slashes.
     */
    public String upload(String bucket, String objectName, byte[] content, MediaType contentType) {
        if (!isEnabled()) {
            throw new IllegalStateException("Supabase storage is not configured");
        }

        restClient.post()
            .uri("/object/{bucket}/{objectName}", bucket, objectName)
            .contentType(contentType)
            .header("x-upsert", "false")
            .body(content)
            .retrieve()
            .toBodilessEntity();

        return publicUrl(bucket, objectName);
    }

    public String publicUrl(String bucket, String objectName) {
        return projectUrl + "/storage/v1/object/public/" + bucket + "/" + objectName;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

}
