/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.azurebfs.services;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.fs.azurebfs.constants.AbfsHttpConstants;
import org.apache.hadoop.fs.azurebfs.constants.HttpHeaderConfigurations;
import org.apache.hadoop.fs.azurebfs.constants.HttpQueryParams;
import org.apache.hadoop.fs.azurebfs.contracts.exceptions.AzureBlobFileSystemException;
import org.apache.hadoop.fs.azurebfs.contracts.exceptions.InvalidUriException;
import org.apache.hadoop.fs.azurebfs.contracts.services.ConfigurationService;
import org.apache.hadoop.fs.azurebfs.contracts.services.LoggingService;
import org.apache.hadoop.fs.azurebfs.oauth2.AccessTokenProvider;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AbfsClient
 */
public class AbfsClient {
  private final URL baseUrl;
  private final SharedKeyCredentials sharedKeyCredentials;
  private final String xMsVersion = "2018-03-28";
  private final ExponentialRetryPolicy retryPolicy;
  private final String filesystem;
  private final LoggingService loggingService;
  private final String userAgent;

  private final String accountFQDN;
  private String accessToken;
  private final AccessTokenProvider tokenProvider;
  private final long clientId;

  public AbfsClient(final URL baseUrl, final SharedKeyCredentials sharedKeyCredentials,
                    final LoggingService loggingService, final ConfigurationService configurationService,
                    final ExponentialRetryPolicy exponentialRetryPolicy,
                    final AccessTokenProvider tokenProvider) {
    this.baseUrl = baseUrl;
    this.sharedKeyCredentials = sharedKeyCredentials;
    String baseUrlString = baseUrl.toString();
    this.filesystem = baseUrlString.substring(baseUrlString.lastIndexOf(AbfsHttpConstants.FORWARD_SLASH) + 1);
    this.loggingService = loggingService.get(AbfsClient.class);
    this.retryPolicy = exponentialRetryPolicy;
    this.userAgent = initializeUserAgent(configurationService);
    this.tokenProvider = tokenProvider;

    this.accountFQDN = null;
    this.accessToken = null;
    this.clientId = 0;

  }

  public String getFileSystem() {
    return filesystem;
  }

  ExponentialRetryPolicy getRetryPolicy() {
    return retryPolicy;
  }

  SharedKeyCredentials getSharedKeyCredentials() {
    return sharedKeyCredentials;
  }

  LoggingService getLoggingService() {
    return loggingService;
  }

  List<AbfsHttpHeader> createDefaultHeaders() {
    final List<AbfsHttpHeader> requestHeaders = new ArrayList<AbfsHttpHeader>();
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_MS_VERSION, xMsVersion));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.ACCEPT, AbfsHttpConstants.APPLICATION_JSON
            + AbfsHttpConstants.COMMA + AbfsHttpConstants.SINGLE_WHITE_SPACE + AbfsHttpConstants.APPLICATION_OCTET_STREAM));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.ACCEPT_CHARSET,
            AbfsHttpConstants.UTF_8));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.CONTENT_TYPE, AbfsHttpConstants.EMPTY_STRING));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.USER_AGENT, userAgent));
    return requestHeaders;
  }

  AbfsUriQueryBuilder createDefaultUriQueryBuilder() {
    final AbfsUriQueryBuilder abfsUriQueryBuilder = new AbfsUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_TIMEOUT, AbfsHttpConstants.DEFAULT_TIMEOUT);
    return abfsUriQueryBuilder;
  }

  public AbfsRestOperation createFilesystem() throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = new AbfsUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, AbfsHttpConstants.FILESYSTEM);

    final URL url = createRequestUrl(abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation setFilesystemProperties(final String properties) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    // JDK 1.7 does not support PATCH, so to workaround the issue we will use
    // PUT and specify the real method in the X-Http-Method-Override header.
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_HTTP_METHOD_OVERRIDE,
            AbfsHttpConstants.HTTP_METHOD_PATCH));

    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_MS_PROPERTIES,
            properties));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, AbfsHttpConstants.FILESYSTEM);

    final URL url = createRequestUrl(abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation listPath(final String relativePath, final boolean recursive, final int listMaxResults,
                                    final String continuation) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, AbfsHttpConstants.FILESYSTEM);
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_DIRECTORY, relativePath == null ? "" : relativePath);
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RECURSIVE, String.valueOf(recursive));
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_CONTINUATION, continuation);
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_MAXRESULTS, String.valueOf(listMaxResults));

    final URL url = createRequestUrl(abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_GET,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation getFilesystemProperties() throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, AbfsHttpConstants.FILESYSTEM);

    final URL url = createRequestUrl(abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_HEAD,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation deleteFilesystem() throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, AbfsHttpConstants.FILESYSTEM);

    final URL url = createRequestUrl(abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_DELETE,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation createPath(final String path, final boolean isFile, final boolean overwrite)
          throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    if (!overwrite) {
      requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.IF_NONE_MATCH, "*"));
    }

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RESOURCE, isFile ? AbfsHttpConstants.FILE : AbfsHttpConstants.DIRECTORY);

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation renamePath(final String source, final String destination, final String continuation)
          throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final String encodedRenameSource = urlEncode(AbfsHttpConstants.FORWARD_SLASH + this.getFileSystem() + source);
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_MS_RENAME_SOURCE, encodedRenameSource));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.IF_NONE_MATCH, AbfsHttpConstants.STAR));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_CONTINUATION, continuation);

    final URL url = createRequestUrl(destination, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation append(final String path, final long position, final byte[] buffer, final int offset,
                                  final int length) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    // JDK 1.7 does not support PATCH, so to workaround the issue we will use
    // PUT and specify the real method in the X-Http-Method-Override header.
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_HTTP_METHOD_OVERRIDE,
            AbfsHttpConstants.HTTP_METHOD_PATCH));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_ACTION, AbfsHttpConstants.APPEND_ACTION);
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_POSITION, Long.toString(position));

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders, buffer, offset, length);
    op.execute();
    return op;
  }


  public AbfsRestOperation flush(final String path, final long position, boolean retainUncommittedData) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    // JDK 1.7 does not support PATCH, so to workaround the issue we will use
    // PUT and specify the real method in the X-Http-Method-Override header.
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_HTTP_METHOD_OVERRIDE,
            AbfsHttpConstants.HTTP_METHOD_PATCH));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_ACTION, AbfsHttpConstants.FLUSH_ACTION);
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_POSITION, Long.toString(position));
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RETAIN_UNCOMMITTED_DATA, String.valueOf(retainUncommittedData));

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation setPathProperties(final String path, final String properties) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    // JDK 1.7 does not support PATCH, so to workaround the issue we will use
    // PUT and specify the real method in the X-Http-Method-Override header.
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_HTTP_METHOD_OVERRIDE,
            AbfsHttpConstants.HTTP_METHOD_PATCH));

    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.X_MS_PROPERTIES, properties));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_ACTION, AbfsHttpConstants.SET_PROPERTIES_ACTION);

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_PUT,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation getPathProperties(final String path) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_HEAD,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  public AbfsRestOperation read(final String path, final long position, final byte[] buffer, final int bufferOffset,
                                final int bufferLength, final String eTag) throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.RANGE,
            String.format("bytes=%d-%d", position, position + bufferLength - 1)));
    requestHeaders.add(new AbfsHttpHeader(HttpHeaderConfigurations.IF_MATCH, eTag));

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());

    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_GET,
            url,
            requestHeaders,
            buffer,
            bufferOffset,
            bufferLength);
    op.execute();

    return op;
  }

  public AbfsRestOperation deletePath(final String path, final boolean recursive, final String continuation)
          throws AzureBlobFileSystemException {
    final List<AbfsHttpHeader> requestHeaders = createDefaultHeaders();

    final AbfsUriQueryBuilder abfsUriQueryBuilder = createDefaultUriQueryBuilder();
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_RECURSIVE, String.valueOf(recursive));
    abfsUriQueryBuilder.addQuery(HttpQueryParams.QUERY_PARAM_CONTINUATION, continuation);

    final URL url = createRequestUrl(path, abfsUriQueryBuilder.toString());
    final AbfsRestOperation op = new AbfsRestOperation(
            this,
            AbfsHttpConstants.HTTP_METHOD_DELETE,
            url,
            requestHeaders);
    op.execute();
    return op;
  }

  private URL createRequestUrl(final String query) throws AzureBlobFileSystemException {
    return createRequestUrl(AbfsHttpConstants.EMPTY_STRING, query);
  }

  private URL createRequestUrl(final String path, final String query)
          throws AzureBlobFileSystemException {
    final String base = baseUrl.toString();
    String encodedPath = path;
    try {
      encodedPath = urlEncode(path);
    } catch (AzureBlobFileSystemException ex) {
      this.loggingService.debug(
              "Unexpected error.", ex);
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(base);
    sb.append(encodedPath);
    sb.append(query);

    final URL url;
    try {
      url = new URL(sb.toString());
    } catch (MalformedURLException ex) {
      throw new InvalidUriException(sb.toString());
    }
    return url;
  }

  public static String urlEncode(final String value) throws AzureBlobFileSystemException {
    String encodedString;
    try {
      encodedString =  URLEncoder.encode(value, AbfsHttpConstants.UTF_8)
          .replace(AbfsHttpConstants.PLUS, AbfsHttpConstants.PLUS_ENCODE)
          .replace(AbfsHttpConstants.FORWARD_SLASH_ENCODE, AbfsHttpConstants.FORWARD_SLASH);
    } catch (UnsupportedEncodingException ex) {
        throw new InvalidUriException(value);
    }

    return encodedString;
  }

  synchronized public String getAccessToken() throws IOException {
    if (tokenProvider != null ) {
      return "Bearer " + tokenProvider.getToken().getAccessToken();
    } else {
      return accessToken;
    }
  }

  @VisibleForTesting
  String initializeUserAgent(final ConfigurationService configurationService) {
    final String userAgentComment = String.format(Locale.ROOT,
            "(JavaJRE %s; %s %s)",
            System.getProperty(AbfsHttpConstants.JAVA_VERSION),
            System.getProperty(AbfsHttpConstants.OS_NAME)
                    .replaceAll(AbfsHttpConstants.SINGLE_WHITE_SPACE, AbfsHttpConstants.EMPTY_STRING),
            System.getProperty(AbfsHttpConstants.OS_VERSION));

    String customUserAgentId = configurationService.getCustomUserAgentPrefix();
    if (customUserAgentId != null && !customUserAgentId.isEmpty()) {
      return String.format(Locale.ROOT, AbfsHttpConstants.CLIENT_VERSION + " %s %s", userAgentComment, customUserAgentId);
    }

    return String.format(AbfsHttpConstants.CLIENT_VERSION + " %s", userAgentComment);
  }
}