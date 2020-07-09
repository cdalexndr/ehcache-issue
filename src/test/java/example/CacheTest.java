package example;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.config.DefaultConfiguration;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.testng.annotations.Test;

public class CacheTest {

  private Cache<Integer, Integer> buildCache() {
    final String CACHE_NAME = "test";
    CachingProvider provider = Caching.getCachingProvider();
    assert provider instanceof EhcacheCachingProvider;
    EhcacheCachingProvider ehcacheProvider = (EhcacheCachingProvider) provider;
    DefaultConfiguration configuration = new DefaultConfiguration(
        ehcacheProvider.getDefaultClassLoader(),
        new DefaultPersistenceConfiguration(new File("./cache")));
    configuration.addCacheConfiguration(CACHE_NAME
        , CacheConfigurationBuilder
            .newCacheConfigurationBuilder(
                Integer.class, Integer.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder().disk(128, MemoryUnit.MB).build())
            .build());
    CacheManager cacheManager = ehcacheProvider
        .getCacheManager(ehcacheProvider.getDefaultURI(), configuration);
    return cacheManager.getCache(CACHE_NAME);
  }

  @Test(timeOut = 10_000)
  public void testInterrupt() throws InterruptedException {
    Cache<Integer, Integer> cache = buildCache();
    assert cache != null;

    AtomicInteger count = new AtomicInteger(0);
    Thread thread = new Thread(() -> {
      while (true) {
        int key = count.incrementAndGet();
        cache.put(key, key);
        if (key % 100 == 0) {
          for (int oldKey = 1; oldKey <= key; ++oldKey) {
            if (!cache.containsKey(oldKey)) {
              return;
            }
          }
        }
      }
    });
    thread.start();
    while (count.get() == 0) {
      Thread.sleep(1);
    }

    thread.interrupt();
    thread.join();

    for (int key = 1; key <= count.get(); ++key) {
      assert cache.containsKey(key);
    }
  }
}
