package com.github.goober.sonarqube.plugin.decorator.bitbucket;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.goober.sonarqube.plugin.decorator.bitbucket.model.CreateAnnotationsRequest;
import com.github.goober.sonarqube.plugin.decorator.bitbucket.model.CreateReportRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;
import java.util.Optional;

import static java.lang.String.format;

@ComputeEngineSide
public class BitbucketClient {
    private static final Logger LOGGER = Loggers.get(BitbucketClient.class);

    private final Configuration configuration;

    private OkHttpClient client;
    private ObjectMapper objectMapper;

    public BitbucketClient(Configuration configuration) {
        this.configuration = configuration;
    }

    boolean isConfigured() {
        return configuration.hasKey(BitbucketProperties.ENDPOINT.getKey()) &&
                configuration.hasKey(BitbucketProperties.TOKEN.getKey());
    }

    void createReport(String project, String repository, String commit, CreateReportRequest request) throws IOException {
        String body = getObjectMapper().writeValueAsString(request);
        Request req = new Request.Builder()
                .put(RequestBody.create(body, MediaType.parse("application/json")))
                .url(format("%s/rest/insights/1.0/projects/%s/repos/%s/commits/%s/reports/com.foobar", baseUrl(), project, repository, commit))
                .build();

        try (Response response = getClient().newCall(req).execute()) {
            validate(response);
        }
    }

    void createAnnotations(String project, String repository, String commit, CreateAnnotationsRequest request) throws IOException {
        Request req = new Request.Builder()
                .post(RequestBody.create(getObjectMapper().writeValueAsString(request), MediaType.parse("application/json")))
                .url(format("%s/rest/insights/1.0/projects/%s/repos/%s/commits/%s/reports/com.foobar/annotations", baseUrl(), project, repository, commit))
                .build();
        try (Response response = getClient().newCall(req).execute()) {
            validate(response);
        }
    }

    void deleteAnnotations(String project, String repository, String commit) throws IOException {
        Request req = new Request.Builder()
                .delete()
                .url(format("%s/rest/insights/1.0/projects/%s/repos/%s/commits/%s/reports/com.foobar/annotations", baseUrl(), project, repository, commit))
                .build();
        try (Response response = getClient().newCall(req).execute()) {
            validate(response);
        }
    }

    private void validate(Response response) throws IOException {
        if(!response.isSuccessful()) {
            LOGGER.error("{} - {}", response.code(), response.body() == null ? "" : response.body().string());
            throw new IOException(format("Bitbucket responded with an unsuccessful response %d", response.code()));
        }
    }

    private OkHttpClient getClient() {
        client = Optional.ofNullable(client).orElseGet(() ->
                new OkHttpClient.Builder()
                        .authenticator(((route, response) ->
                                response.request()
                                        .newBuilder()
                                        .header("Authorization", format("Bearer %s", getToken()))
                                        .build()
                        ))
                        .build()
        );
        return client;
    }

    private ObjectMapper getObjectMapper() {
        objectMapper = Optional.ofNullable(objectMapper).orElseGet(() -> new ObjectMapper()
                .setSerializationInclusion(Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        );
        return objectMapper;
    }

    private String baseUrl() {
        return configuration.get(BitbucketProperties.ENDPOINT.getKey())
                .orElseThrow(() ->
                        new IllegalArgumentException(format("Missing required property %s", BitbucketProperties.ENDPOINT.getKey()))
                );
    }

    private String getToken() {
        return configuration.get(BitbucketProperties.TOKEN.getKey())
                .orElseThrow(() -> new IllegalArgumentException("Personal Access Token for Bitbucket Server is missing"));
    }
}