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

package org.apache.hadoop.fs.azuredfs;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import org.apache.hadoop.fs.AbstractFileSystem;
import org.apache.hadoop.fs.CommonConfigurationKeysPublic;
import org.apache.hadoop.fs.FileContext;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.azuredfs.constants.FileSystemUriSchemes;
import org.apache.hadoop.fs.azuredfs.contracts.services.AdfsHttpService;
import org.apache.hadoop.fs.azuredfs.services.ServiceProviderImpl;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doReturn;

public class ITestFileSystemRegistration extends DependencyInjectedTest {
  public ITestFileSystemRegistration() throws Exception {
    super();

    this.mockServiceInjector.removeProvider(AdfsHttpService.class);
    this.mockServiceInjector.replaceInstance(AdfsHttpService.class, Mockito.mock(AdfsHttpService.class));
  }

  @Test
  public void ensureAzureDistributedFileSystemIsDefaultFileSystem() throws Exception {
    doReturn(new FileStatus(0, true, 0, 0, 0, new Path("/blah")))
        .when(ServiceProviderImpl.instance().get(AdfsHttpService.class))
        .getFileStatus((AzureDistributedFileSystem) anyObject(), (Path) anyObject());

    FileSystem fs = FileSystem.get(this.getConfiguration());
    Assert.assertTrue(fs instanceof AzureDistributedFileSystem);

    AbstractFileSystem afs = FileContext.getFileContext(this.getConfiguration()).getDefaultFileSystem();
    Assert.assertTrue(afs instanceof Adfs);
  }

  @Test
  public void ensureSecureAzureDistributedIsDefaultFileSystem() throws Exception {
    doReturn(new FileStatus(0, true, 0, 0, 0, new Path("/blah")))
        .when(ServiceProviderImpl.instance().get(AdfsHttpService.class))
        .getFileStatus((AzureDistributedFileSystem) anyObject(), (Path) anyObject());

    final String accountName = this.getAccountName();
    final String filesystem = this.getFileSystemName();

    final URI defaultUri = new URI(FileSystemUriSchemes.ADFS_SECURE_SCHEME, filesystem + "@" + accountName, null, null, null);
    this.getConfiguration().set(CommonConfigurationKeysPublic.FS_DEFAULT_NAME_KEY, defaultUri.toString());

    FileSystem fs = FileSystem.get(this.getConfiguration());
    Assert.assertTrue(fs instanceof SecureAzureDistributedFileSystem);

    AbstractFileSystem afs = FileContext.getFileContext(this.getConfiguration()).getDefaultFileSystem();
    Assert.assertTrue(afs instanceof Adfss);
  }
}