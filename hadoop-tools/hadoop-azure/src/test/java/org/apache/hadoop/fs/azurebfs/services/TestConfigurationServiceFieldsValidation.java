/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.azurebfs.services;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import org.junit.Test;

import com.microsoft.azure.storage.core.Base64;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.azurebfs.constants.ConfigurationKeys;
import org.apache.hadoop.fs.azurebfs.contracts.annotations.ConfigurationValidationAnnotations.*;
import org.apache.hadoop.fs.azurebfs.contracts.exceptions.ConfigurationPropertyNotFoundException;

import static org.apache.hadoop.fs.azurebfs.constants.FileSystemConfigurations.*;

public class TestConfigurationServiceFieldsValidation  {
  private ConfigurationServiceImpl configService;

  private static final String INT_KEY= "intKey";
  private static final String LONG_KEY= "longKey";
  private static final String STRING_KEY= "stringKey";
  private static final String BASE64_KEY= "base64Key";
  private static final String BOOLEAN_KEY= "booleanKey";
  private static final int DEFAULT_INT = 4194304;
  private static final int DEFAULT_LONG = 4194304;
  private final String encodedString;
  private final String encodedAccountKey;

  @IntegerConfigurationValidatorAnnotation(ConfigurationKey = INT_KEY,
      MinValue = Integer.MIN_VALUE,
      MaxValue = Integer.MAX_VALUE,
      DefaultValue = DEFAULT_INT)
  private int intField;

  @LongConfigurationValidatorAnnotation(ConfigurationKey = LONG_KEY,
      MinValue = Long.MIN_VALUE,
      MaxValue = Long.MAX_VALUE,
      DefaultValue = DEFAULT_LONG)
  private int longField;

  @StringConfigurationValidatorAnnotation(ConfigurationKey = STRING_KEY,
  DefaultValue = "default")
  private String stringField;

  @Base64StringConfigurationValidatorAnnotation(ConfigurationKey = BASE64_KEY,
  DefaultValue = "base64")
  private String base64Field;

  @BooleanConfigurationValidatorAnnotation(ConfigurationKey = BOOLEAN_KEY,
  DefaultValue = false)
  private boolean boolField;

  public TestConfigurationServiceFieldsValidation() throws Exception {
    super();
    this.encodedString = Base64.encode("base64Value".getBytes());
    this.encodedAccountKey = Base64.encode("someAccountKey".getBytes());
    Configuration configuration = new Configuration();
    configuration.addResource("azure-bfs-test.xml");
    configuration.set(INT_KEY, "1234565");
    configuration.set(LONG_KEY, "4194304");
    configuration.set(STRING_KEY, "stringValue");
    configuration.set(BASE64_KEY, encodedString);
    configuration.set(BOOLEAN_KEY, "true");
    configuration.set(ConfigurationKeys.FS_AZURE_ACCOUNT_KEY_PROPERTY_NAME + "testaccount1.blob.core.windows.net", this.encodedAccountKey);
    configService = new ConfigurationServiceImpl(configuration, new LoggingServiceImpl());
  }

  @Test
  public void testValidateFunctionsInConfigServiceImpl() throws Exception {
    Field[] fields = this.getClass().getDeclaredFields();
    for (Field field : fields) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(IntegerConfigurationValidatorAnnotation.class)) {
        assertEquals(1234565, configService.validateInt(field));
      } else if (field.isAnnotationPresent(LongConfigurationValidatorAnnotation.class)) {
        assertEquals(DEFAULT_LONG, configService.validateLong(field));
      } else if (field.isAnnotationPresent(StringConfigurationValidatorAnnotation.class)) {
        assertEquals("stringValue", configService.validateString(field));
      } else if (field.isAnnotationPresent(Base64StringConfigurationValidatorAnnotation.class)) {
        assertEquals(this.encodedString , configService.validateBase64String(field));
      } else if (field.isAnnotationPresent(BooleanConfigurationValidatorAnnotation.class)) {
        assertEquals(true, configService.validateBoolean(field));
      }
    }
  }

  @Test
  public void testConfigServiceImplAnnotatedFieldsInitialized() throws Exception {
    // test that all the ConfigurationServiceImpl annotated fields have been initialized in the constructor
    assertEquals(DEFAULT_WRITE_BUFFER_SIZE, configService.getWriteBufferSize());
    assertEquals(DEFAULT_READ_BUFFER_SIZE, configService.getReadBufferSize());
    assertEquals(DEFAULT_MIN_BACKOFF_INTERVAL, configService.getMinBackoffIntervalMilliseconds());
    assertEquals(DEFAULT_MAX_BACKOFF_INTERVAL, configService.getMaxBackoffIntervalMilliseconds());
    assertEquals(DEFAULT_BACKOFF_INTERVAL, configService.getBackoffIntervalMilliseconds());
    assertEquals(DEFAULT_MAX_RETRY_ATTEMPTS, configService.getMaxIoRetries());
    assertEquals(MAX_AZURE_BLOCK_SIZE, configService.getAzureBlockSize());
    assertEquals(AZURE_BLOCK_LOCATION_HOST_DEFAULT, configService.getAzureBlockLocationHost());
  }

  @Test
  public void testGetAccountKey() throws Exception {
    String accountKey = configService.getStorageAccountKey("testaccount1.blob.core.windows.net");
    assertEquals(this.encodedAccountKey, accountKey);
  }

  @Test (expected = ConfigurationPropertyNotFoundException.class)
  public void testGetAccountKeyWithNonExistingAccountName() throws Exception {
    configService.getStorageAccountKey("bogusAccountName");
  }
}
