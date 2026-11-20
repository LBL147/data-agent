package com.pangruixin.llm.mentor.agentx.service;

import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketPolicyArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioServiceBucketPolicyTest {

    private static final String BUCKET = "test-bucket";

    private MinioClient minioClient;
    private MinioService service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        service = new MinioService(minioClient);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET);
    }

    @Test
    void existingBucketHasPublicPolicyRemoved() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doNothing().when(minioClient).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));

        service.ensureBucket();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void newBucketIsCreatedWithoutPublicPolicy() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));
        doNothing().when(minioClient).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));

        service.ensureBucket();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void missingBucketPolicyIsTreatedAsPrivate() throws Exception {
        ErrorResponse error = new ErrorResponse(
                "NoSuchBucketPolicy", "The bucket policy does not exist",
                BUCKET, null, null, null, null);
        ErrorResponseException missingPolicy =
                new ErrorResponseException(error, null, "");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doThrow(missingPolicy).when(minioClient)
                .deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));

        assertDoesNotThrow(() -> service.ensureBucket());

        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }
}
