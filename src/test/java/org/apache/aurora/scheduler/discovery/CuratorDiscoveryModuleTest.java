/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler.discovery;

import java.net.InetSocketAddress;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.aurora.common.application.ShutdownRegistry;
import org.apache.aurora.common.application.ShutdownRegistry.ShutdownRegistryImpl;
import org.apache.aurora.common.quantity.Amount;
import org.apache.aurora.common.quantity.Time;
import org.apache.aurora.common.testing.TearDownTestCase;
import org.apache.aurora.common.zookeeper.Credentials;
import org.apache.aurora.common.zookeeper.SingletonService;
import org.apache.aurora.common.zookeeper.ZooKeeperUtils;
import org.apache.aurora.scheduler.app.ServiceGroupMonitor;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.zookeeper.data.ACL;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CuratorDiscoveryModuleTest extends TearDownTestCase {

  @Test
  public void testSingleACLProvider() {
    ImmutableList<ACL> acl = ZooKeeperUtils.EVERYONE_READ_CREATOR_ALL;
    ACLProvider provider = new CuratorServiceDiscoveryModule.SingleACLProvider(acl);

    assertEquals(acl, provider.getDefaultAcl());
    assertEquals(acl, provider.getAclForPath("/random/path/1"));
    assertEquals(acl, provider.getAclForPath("/random/path/2"));
  }

  @Test(expected = NullPointerException.class)
  public void testSingleACLProviderNull() {
    new CuratorServiceDiscoveryModule.SingleACLProvider(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSingleACLProviderEmpty() {
    new CuratorServiceDiscoveryModule.SingleACLProvider(ImmutableList.of());
  }

  @Test
  public void testBindingContract() {
    ZooKeeperConfig zooKeeperConfig =
        new ZooKeeperConfig(
            ImmutableList.of(InetSocketAddress.createUnresolved("localhost", 42)),
            Optional.of("/chroot"),
            false, // inProcess
            Amount.of(1, Time.DAYS),
            Optional.of(Credentials.digestCredentials("test", "user")));

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ServiceDiscoveryBindings.ZOO_KEEPER_CLUSTER_KEY)
                    .toInstance(
                        ImmutableList.of(InetSocketAddress.createUnresolved("localhost", 42)));
                bind(ServiceDiscoveryBindings.ZOO_KEEPER_ACL_KEY)
                    .toInstance(ZooKeeperUtils.OPEN_ACL_UNSAFE);

                ShutdownRegistryImpl shutdownRegistry = new ShutdownRegistryImpl();
                binder().bind(ShutdownRegistry.class).toInstance(shutdownRegistry);
                addTearDown(shutdownRegistry::execute);
              }
            },
            new CuratorServiceDiscoveryModule("/discovery/path", zooKeeperConfig));

    assertNotNull(injector.getBinding(SingletonService.class).getProvider().get());
    assertNotNull(injector.getBinding(ServiceGroupMonitor.class).getProvider().get());
  }
}
