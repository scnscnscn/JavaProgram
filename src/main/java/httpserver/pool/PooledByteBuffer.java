package httpserver.pool;

import java.nio.ByteBuffer;

/**
 * 可池化的ByteBuffer，用于网络I/O操作
 */
public class PooledByteBuffer implements Poolable {
    private ByteBuffer buffer;
    private static final int DEFAULT_SIZE = 8192; // 8KB
    private static final int MAX_SIZE = 65536;    // 64KB
    
    public PooledByteBuffer() {
        this.buffer = ByteBuffer.allocate(DEFAULT_SIZE);
    }
    
    public PooledByteBuffer(int size) {
        this.buffer = ByteBuffer.allocate(Math.min(size, MAX_SIZE));
    }
    
    public ByteBuffer getBuffer() {
        return buffer;
    }
    
    public int capacity() {
        return buffer.capacity();
    }
    
    public int remaining() {
        return buffer.remaining();
    }
    
    public void put(byte[] src) {
        buffer.put(src);
    }
    
    public void put(byte b) {
        buffer.put(b);
    }
    
    public byte[] array() {
        return buffer.array();
    }
    
    public void flip() {
        buffer.flip();
    }
    
    public void clear() {
        buffer.clear();
    }
    
    @Override
    public void reset() {
        // 如果buffer太大，重新创建一个默认大小的
        if (buffer.capacity() > MAX_SIZE) {
            buffer = ByteBuffer.allocate(DEFAULT_SIZE);
        } else {
            buffer.clear(); // 重置position和limit
        }
    }
}