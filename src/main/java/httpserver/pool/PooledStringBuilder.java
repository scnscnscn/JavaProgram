package httpserver.pool;

/**
 * 可池化的StringBuilder，用于减少字符串拼接时的内存分配
 */
public class PooledStringBuilder implements Poolable {
    private StringBuilder sb;
    private static final int INITIAL_CAPACITY = 256;
    private static final int MAX_CAPACITY = 8192; // 最大容量，防止内存泄漏
    
    public PooledStringBuilder() {
        this.sb = new StringBuilder(INITIAL_CAPACITY);
    }
    
    public PooledStringBuilder append(String str) {
        sb.append(str);
        return this;
    }
    
    public PooledStringBuilder append(char c) {
        sb.append(c);
        return this;
    }
    
    public PooledStringBuilder append(int i) {
        sb.append(i);
        return this;
    }
    
    public PooledStringBuilder append(long l) {
        sb.append(l);
        return this;
    }
    
    public int length() {
        return sb.length();
    }
    
    public String toString() {
        return sb.toString();
    }
    
    @Override
    public void reset() {
        // 如果StringBuilder太大，重新创建一个小的
        if (sb.capacity() > MAX_CAPACITY) {
            sb = new StringBuilder(INITIAL_CAPACITY);
        } else {
            sb.setLength(0); // 清空内容但保留容量
        }
    }
    
    public StringBuilder getStringBuilder() {
        return sb;
    }
}