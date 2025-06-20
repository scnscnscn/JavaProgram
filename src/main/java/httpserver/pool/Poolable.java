package httpserver.pool;

/**
 * 可池化对象接口
 * 实现此接口的对象在归还到池中时会调用reset方法进行重置
 */
public interface Poolable {
    /**
     * 重置对象状态，准备被重新使用
     */
    void reset();
}