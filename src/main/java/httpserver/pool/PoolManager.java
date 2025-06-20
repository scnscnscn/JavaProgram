package httpserver.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 内存池管理器，统一管理所有内存池
 * 提供池的创建、获取、监控和清理功能
 */
public class PoolManager {
    private static final PoolManager INSTANCE = new PoolManager();
    private final Map<String, MemoryPool<?>> pools;
    private final ScheduledExecutorService scheduler;
    
    // 预定义的常用池
    public static final String STRING_BUILDER_POOL = "StringBuilder";
    public static final String BYTE_BUFFER_POOL = "ByteBuffer";
    public static final String HTTP_RESPONSE_POOL = "HttpResponse";
    
    private PoolManager() {
        this.pools = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PoolManager-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        initializePools();
        startMonitoring();
    }
    
    public static PoolManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 初始化常用的内存池
     */
    private void initializePools() {
        // StringBuilder池 - 用于字符串拼接
        registerPool(STRING_BUILDER_POOL, 
                    new MemoryPool<>(STRING_BUILDER_POOL, PooledStringBuilder::new, 100));
        
        // ByteBuffer池 - 用于网络I/O
        registerPool(BYTE_BUFFER_POOL, 
                    new MemoryPool<>(BYTE_BUFFER_POOL, PooledByteBuffer::new, 50));
        
        // HTTP响应池 - 用于HTTP响应构建
        registerPool(HTTP_RESPONSE_POOL, 
                    new MemoryPool<>(HTTP_RESPONSE_POOL, HttpResponseBuilder::new, 200));
    }
    
    /**
     * 注册内存池
     */
    public <T> void registerPool(String name, MemoryPool<T> pool) {
        pools.put(name, pool);
        System.out.println("内存池已注册: " + name);
    }
    
    /**
     * 获取指定名称的内存池
     */
    @SuppressWarnings("unchecked")
    public <T> MemoryPool<T> getPool(String name) {
        return (MemoryPool<T>) pools.get(name);
    }
    
    /**
     * 获取StringBuilder池中的对象
     */
    public PooledStringBuilder getStringBuilder() {
        MemoryPool<PooledStringBuilder> pool = getPool(STRING_BUILDER_POOL);
        return pool != null ? pool.acquire() : new PooledStringBuilder();
    }
    
    /**
     * 归还StringBuilder到池中
     */
    public void releaseStringBuilder(PooledStringBuilder sb) {
        MemoryPool<PooledStringBuilder> pool = getPool(STRING_BUILDER_POOL);
        if (pool != null) {
            pool.release(sb);
        }
    }
    
    /**
     * 获取ByteBuffer池中的对象
     */
    public PooledByteBuffer getByteBuffer() {
        MemoryPool<PooledByteBuffer> pool = getPool(BYTE_BUFFER_POOL);
        return pool != null ? pool.acquire() : new PooledByteBuffer();
    }
    
    /**
     * 归还ByteBuffer到池中
     */
    public void releaseByteBuffer(PooledByteBuffer buffer) {
        MemoryPool<PooledByteBuffer> pool = getPool(BYTE_BUFFER_POOL);
        if (pool != null) {
            pool.release(buffer);
        }
    }
    
    /**
     * 获取HTTP响应构建器
     */
    public HttpResponseBuilder getHttpResponseBuilder() {
        MemoryPool<HttpResponseBuilder> pool = getPool(HTTP_RESPONSE_POOL);
        return pool != null ? pool.acquire() : new HttpResponseBuilder();
    }
    
    /**
     * 归还HTTP响应构建器到池中
     */
    public void releaseHttpResponseBuilder(HttpResponseBuilder builder) {
        MemoryPool<HttpResponseBuilder> pool = getPool(HTTP_RESPONSE_POOL);
        if (pool != null) {
            pool.release(builder);
        }
    }
    
    /**
     * 开始监控所有池的状态
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                printPoolStats();
            } catch (Exception e) {
                System.err.println("池监控异常: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS); // 每分钟打印一次统计信息
    }
    
    /**
     * 打印所有池的统计信息
     */
    public void printPoolStats() {
        System.out.println("\n=== 内存池统计信息 ===");
        pools.forEach((name, pool) -> {
            System.out.println(pool.getStats());
        });
        System.out.println("=====================\n");
    }
    
    /**
     * 清空所有池
     */
    public void clearAllPools() {
        pools.values().forEach(MemoryPool::clear);
        System.out.println("所有内存池已清空");
    }
    
    /**
     * 关闭池管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        clearAllPools();
        System.out.println("内存池管理器已关闭");
    }
}