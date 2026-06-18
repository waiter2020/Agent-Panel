package com.agentpanel.storage;

import com.agentpanel.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;

@Service
public class StorageService {

    private final StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public StorageService(StorageProperties properties) {
        this.properties = properties;
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var builder = S3Client.builder()
                .credentialsProvider(credentials)
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .forcePathStyle(properties.pathStyleAccess());
        this.s3Client = builder.build();
        this.presigner = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(Region.of(properties.region()))
                .endpointOverride(URI.create(properties.endpoint()))
                .build();
        ensureBucket();
    }

    public void putObject(String key, InputStream in, long size, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build(), software.amazon.awssdk.core.sync.RequestBody.fromInputStream(in, size));
    }

    public InputStream getObject(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    public URL presignedGetUrl(String key, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(properties.bucket()).key(key))
                .build()).url();
    }

    public URL presignedPutUrl(String key, Duration ttl) {
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(r -> r.bucket(properties.bucket()).key(key))
                .build()).url();
    }

    public List<ObjectInfo> list(String prefix) {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(prefix == null ? "" : prefix)
                .build());
        return response.contents().stream()
                .map(o -> new ObjectInfo(o.key(), o.size(), o.lastModified()))
                .toList();
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        }
    }

    public record ObjectInfo(String key, long size, java.time.Instant lastModified) {}
}
