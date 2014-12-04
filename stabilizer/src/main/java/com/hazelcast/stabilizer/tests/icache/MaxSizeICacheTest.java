package com.hazelcast.stabilizer.tests.icache;

import com.hazelcast.cache.ICache;
import com.hazelcast.cache.impl.HazelcastServerCacheManager;
import com.hazelcast.cache.impl.HazelcastServerCachingProvider;
import com.hazelcast.client.cache.impl.HazelcastClientCacheManager;
import com.hazelcast.client.cache.impl.HazelcastClientCachingProvider;

import com.hazelcast.config.CacheConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.stabilizer.tests.TestContext;
import com.hazelcast.stabilizer.tests.annotations.Run;
import com.hazelcast.stabilizer.tests.annotations.Setup;
import com.hazelcast.stabilizer.tests.annotations.Verify;
import com.hazelcast.stabilizer.tests.utils.TestUtils;
import com.hazelcast.stabilizer.tests.utils.ThreadSpawner;

import javax.cache.CacheManager;
import java.util.Random;

import static junit.framework.Assert.assertTrue;

public class MaxSizeICacheTest {

    private final static ILogger log = Logger.getLogger(MaxSizeICacheTest.class);
    public int threadCount=3;
    public int valueSize=100;
    public double sizeMargin=0.2;
    public String basename;

    private String id;
    private TestContext testContext;
    private HazelcastInstance targetInstance;
    private byte[] value;
    private ICache<Object, Object> cache;
    private int maxSize;
    private int threshold;

    @Setup
    public void setup(TestContext testContex) throws Exception {
        this.testContext = testContex;
        targetInstance = testContext.getTargetInstance();
        id=testContex.getTestId();

        value = new byte[valueSize];
        Random random = new Random();
        random.nextBytes(value);

        CacheManager cacheManager;
        if (TestUtils.isMemberNode(targetInstance)) {
            HazelcastServerCachingProvider hcp = new HazelcastServerCachingProvider();
            cacheManager = new HazelcastServerCacheManager(
                    hcp, targetInstance, hcp.getDefaultURI(), hcp.getDefaultClassLoader(), null);
        } else {
            HazelcastClientCachingProvider hcp = new HazelcastClientCachingProvider();
            cacheManager = new HazelcastClientCacheManager(
                    hcp, targetInstance, hcp.getDefaultURI(), hcp.getDefaultClassLoader(), null);
        }
        cache = (ICache) cacheManager.getCache(basename);

        CacheConfig config = cache.getConfiguration(CacheConfig.class);
        log.info(id+": "+cache.getName()+" config="+config);

        maxSize = config.getMaxSizeConfig().getSize();
        threshold = (int) (maxSize * sizeMargin) + maxSize;
    }

    @Run
    public void run() {
        ThreadSpawner spawner = new ThreadSpawner(testContext.getTestId());
        for(int i=0; i<threadCount; i++){
            spawner.spawn( new WorkerThread() );
        }
        spawner.awaitCompletion();
    }

    private class WorkerThread implements Runnable {
        Random random = new Random();
        int max=0;

        public void run() {
            while (!testContext.isStopped()) {

                int key = random.nextInt();
                cache.put(key, value);

                int size = cache.size();
                if(max < size){
                    max = size;
                }

                assertTrue(id+": cache "+cache.getName()+" size="+cache.size()+" max="+maxSize+" threshold="+ threshold, size < threshold);
            }
            targetInstance.getList(basename+"max").add(max);
        }
    }

    @Verify(global = true)
    public void globalVerify() throws Exception {
        IList<Integer> results = targetInstance.getList(basename+"max");
        int max=0;
        for (int m : results) {
            if(max < m){
                max = m;
            }
        }
        log.info(id + ": cache "+cache.getName()+" max size ="+max);
    }
}