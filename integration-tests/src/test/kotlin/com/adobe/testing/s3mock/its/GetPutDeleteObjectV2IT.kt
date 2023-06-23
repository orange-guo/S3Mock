/*
 *  Copyright 2017-2023 Adobe.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.adobe.testing.s3mock.its

import com.adobe.testing.s3mock.util.DigestUtil
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

internal class GetPutDeleteObjectV2IT : S3TestBase() {

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testPutObject_etagCreation(testInfo: TestInfo) {
    val uploadFile = File(UPLOAD_FILE_NAME)
    val uploadFileIs: InputStream = FileInputStream(uploadFile)
    val expectedEtag = "\"${DigestUtil.hexDigest(uploadFileIs)}\""

    val (_, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    assertThat(eTag).isNotBlank
    assertThat(eTag).isEqualTo(expectedEtag)
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testPutGetDeleteObject_twoBuckets(testInfo: TestInfo) {
    val bucket1 = givenRandomBucketV2()
    val bucket2 = givenRandomBucketV2()
    givenObjectV2(bucket1, UPLOAD_FILE_NAME)
    givenObjectV2(bucket2, UPLOAD_FILE_NAME)
    getObjectV2(bucket1, UPLOAD_FILE_NAME)
    val object2 = getObjectV2(bucket2, UPLOAD_FILE_NAME)

    deleteObjectV2(bucket1, UPLOAD_FILE_NAME)
    Assertions.assertThatThrownBy {
      getObjectV2(bucket1, UPLOAD_FILE_NAME)
    }.isInstanceOf(S3Exception::class.java)
      .hasMessageContaining("Service: S3, Status Code: 404")

    val object2Again = getObjectV2(bucket2, UPLOAD_FILE_NAME)
    assertThat(object2.response().eTag()).isEqualTo(object2Again.response().eTag())
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testGetObject_successWithMatchingEtag(testInfo: TestInfo) {
    val uploadFile = File(UPLOAD_FILE_NAME)
    val uploadFileIs: InputStream = FileInputStream(uploadFile)
    val matchingEtag = "\"${DigestUtil.hexDigest(uploadFileIs)}\""

    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    assertThat(eTag).isEqualTo(matchingEtag)
    val responseInputStream = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(UPLOAD_FILE_NAME)
        .ifMatch(matchingEtag)
        .build()
    )
    assertThat(responseInputStream.response().eTag()).isEqualTo(eTag)
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testGetObject_successWithMatchingWildcardEtag(testInfo: TestInfo) {
    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    val matchingEtag = "\"*\""

    val responseInputStream = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(UPLOAD_FILE_NAME)
        .ifMatch(matchingEtag)
        .build()
    )
    assertThat(responseInputStream.response().eTag()).isEqualTo(eTag)
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testHeadObject_successWithNonMatchEtag(testInfo: TestInfo) {
    val uploadFile = File(UPLOAD_FILE_NAME)
    val uploadFileIs: InputStream = FileInputStream(uploadFile)
    val expectedEtag = "\"${DigestUtil.hexDigest(uploadFileIs)}\""

    val nonMatchingEtag = "\"$randomName\""

    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    assertThat(eTag).isEqualTo(expectedEtag)

    val headObjectResponse = s3ClientV2.headObject(
      HeadObjectRequest.builder()
        .bucket(bucketName)
        .key(UPLOAD_FILE_NAME)
        .ifNoneMatch(nonMatchingEtag)
        .build()
    )
    assertThat(headObjectResponse.eTag()).isEqualTo(eTag)
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testHeadObject_failureWithNonMatchWildcardEtag(testInfo: TestInfo) {
    val uploadFile = File(UPLOAD_FILE_NAME)
    val uploadFileIs: InputStream = FileInputStream(uploadFile)
    val expectedEtag = "\"${DigestUtil.hexDigest(uploadFileIs)}\""

    val nonMatchingEtag = "\"*\""

    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    assertThat(eTag).isEqualTo(expectedEtag)

    Assertions.assertThatThrownBy {
      s3ClientV2.headObject(
        HeadObjectRequest.builder()
          .bucket(bucketName)
          .key(UPLOAD_FILE_NAME)
          .ifNoneMatch(nonMatchingEtag)
          .build()
      )
    }.isInstanceOf(S3Exception::class.java)
      .hasMessageContaining("Service: S3, Status Code: 304")
  }

  @Test
  @S3VerifiedSuccess(year = 2022)
  fun testHeadObject_failureWithMatchEtag(testInfo: TestInfo) {
    val uploadFile = File(UPLOAD_FILE_NAME)
    val uploadFileIs: InputStream = FileInputStream(uploadFile)
    val expectedEtag = "\"${DigestUtil.hexDigest(uploadFileIs)}\""

    val nonMatchingEtag = "\"$randomName\""

    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    assertThat(eTag).isEqualTo(expectedEtag)

    Assertions.assertThatThrownBy {
      s3ClientV2.headObject(
        HeadObjectRequest.builder()
          .bucket(bucketName)
          .key(UPLOAD_FILE_NAME)
          .ifMatch(nonMatchingEtag)
          .build()
      )
    }.isInstanceOf(S3Exception::class.java)
      .hasMessageContaining("Service: S3, Status Code: 412")
  }

  @Test
  @S3VerifiedTodo
  fun testGetObject_rangeDownloads(testInfo: TestInfo) {
    val (bucketName, putObjectResponse) = givenBucketAndObjectV2(testInfo, UPLOAD_FILE_NAME)
    val eTag = putObjectResponse.eTag()
    val smallObject = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(UPLOAD_FILE_NAME)
        .ifMatch(eTag)
        .range("bytes=1-2")
        .build()
    )
    assertThat(smallObject.response().contentLength())
      .`as`("Invalid file length")
      .isEqualTo(2L)
    assertThat(smallObject.response().contentRange()).isEqualTo("bytes 1-2/36")

    val largeObject = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(UPLOAD_FILE_NAME)
        .range("bytes=0-1000")
        .build()
    )
    assertThat(largeObject.response().contentLength())
      .`as`("Invalid file length")
      .isEqualTo(36L)
    assertThat(largeObject.response().contentRange()).isEqualTo("bytes 0-35/36")
  }

  @Test
  @S3VerifiedTodo
  fun testGetObject_rangeDownloads_finalBytes_prefixOffset(testInfo: TestInfo) {
    val bucketName = givenBucketV2(testInfo)
    val key = givenObjectV2WithRandomBytes(bucketName)

    val largeObject = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .range("bytes=4500-")
        .build()
    )
    assertThat(largeObject.response().contentLength())
      .`as`("Invalid file length")
      .isEqualTo(5238380L)
    assertThat(largeObject.response().contentRange()).isEqualTo("bytes 4500-5242879/5242880")
  }

  @Test
  @S3VerifiedTodo
  fun testGetObject_rangeDownloads_finalBytes_suffixOffset(testInfo: TestInfo) {
    val bucketName = givenBucketV2(testInfo)
    val key = givenObjectV2WithRandomBytes(bucketName)

    val getObject = s3ClientV2.getObject(
      GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .range("bytes=-500")
        .build()
    )
    assertThat(getObject.response().contentLength())
      .`as`("Invalid file length")
      .isEqualTo(500L)
    assertThat(getObject.response().contentRange()).isEqualTo("bytes 5242380-5242879/5242880")
  }

  fun givenObjectV2WithRandomBytes(bucketName: String): String {
    val key = randomName
    s3ClientV2.putObject(
      PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build(),
      RequestBody.fromBytes(random5MBytes())
    )
    return key
  }
}
