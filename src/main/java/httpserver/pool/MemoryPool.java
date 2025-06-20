package httpserver.pool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 通用内存池实现，类似于TCMalloc的设计理念
 * 支持多线程并发访问，减少对象创建和GC压力
 */
public class MemoryPool<T> {
    private final ConcurrentLinkedQueue<T> pool;
    private final Supplier<T> factory;
    private final int maxSize;
    private final AtomicInteger currentSize;
    private final AtomicLong totalCreated;
    private final AtomicLong totalReused;
    private final String poolName;
    
    public MemoryPool(String poolName, Supplier<T> factory, int maxSize) {
        this.poolName = poolName;
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.maxSize = maxSize;
        this.currentSize = new AtomicInteger(0);
        this.totalCreated = new AtomicLong(0);
        this.totalReused = new AtomicLong(0);
        
        // 预热池，创建一些初始对象
        preWarmPool();
    }
    
    /**
     * 从池中获取对象
     */
    public T acquire() {
        T object = pool.poll();
        if (object != null) {
            currentSize.decrementAndGet();
            totalReused.incrementAndGet();
            return object;
        }
        
        // 池中没有可用对象，创建新的
        totalCreated.incrementAndGet();
        return factory.get();
    }
    
    /**
     * 将对象归还到池中
     */
    public void release(T object) {
        if (object == null) return;
        
        // 如果池未满，则归还对象
        if (currentSize.get() < maxSize) {
            // 如果对象实现了Poolable接口，调用reset方法
            if (object instanceof Poolable) {
                ((Poolable) object).reset();
            }
            
            pool.offer(object);
            currentSize.incrementAndGet();
        }
        // 如果池已满，让对象被GC回收
    }
    
    /**
     * 预热池，创建初始对象
     */
    private void preWarmPool() {
        int preWarmSize = Math.min(maxSize / 4, 10); // 预热25%或最多10个对象
        for (int i = 0; i < preWarmSize; i++) {
            T object = factory.get();
            pool.offer(object);
            currentSize.incrementAndGet();
            totalCreated.incrementAndGet();
        }
    }
    
    /**
     * 获取池统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(
            poolName,
            currentSize.get(),
            maxSize,
            totalCreated.get(),
            totalReused.get()
        );
    }
    
    /**
     * 清空池
     */
    public void clear() {
        pool.clear();
        currentSize.set(0);
    }
    
    /**
     * 池统计信息
     */
    public static class PoolStats {
        private final String poolName;
        private final int currentSize;
        private final int maxSize;
        private final long totalCreated;
        private final long totalReused;
        
        public PoolStats(String poolName, int currentSize, int maxSize, 
                        long totalCreated, long totalReused) {
            this.poolName = poolName;
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.totalCreated = totalCreated;
            this.totalReused = totalReused;
        }
        
        public double getReuseRate() {
            long total = totalCreated + totalReused;
            return total > 0 ? (double) totalReused / total * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Pool[%s]: 当前大小=%d/%d, 总创建=%d, 总复用=%d, 复用率=%.2f%%",
                poolName, currentSize, maxSize, totalCreated, totalReused, getReuseRate()
            );
        }
        
        // Getters
        public String getPoolName() { return poolName; }
        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public long getTotalCreated() { return totalCreated; }
        public long getTotalReused() { return totalReused; }
    }
}