package org.jetlinks.collector.subscribe;

import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.collector.PointData;
import org.jetlinks.collector.Result;
import org.jetlinks.collector.Health;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AbstractPointSubscription 单元测试
 * 
 * @author zhouhao
 */
class AbstractPointSubscriptionTest {

    @Mock
    private PointListener mockListener;
    
    @Mock
    private Monitor mockMonitor;
    
    private TestPointSubscription subscription;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        subscription = new TestPointSubscription(mockListener);
    }

    @Test
    void testConstructor() {
        // 验证构造器正确初始化
        assertNotNull(subscription);
        assertEquals(mockListener, subscription.listener);
        assertFalse(subscription.isDisposed());
        assertTrue(subscription.isEmpty());
    }

    @Test
    void testSubscribeSinglePoint() throws InterruptedException {
        // 准备测试数据
        String pointId = "test-point-1";
        TestPointRuntime runtime = new TestPointRuntime(pointId);
        subscription.addMockRuntime(pointId, runtime);
        
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        
        // 执行订阅
        subscription.subscribe(Collections.singletonList(pointId));
        
        // 验证订阅完成
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        assertTrue(subscription.subscribed(pointId));
        assertTrue(subscription.containsKey(pointId));
        assertEquals(1, subscription.size());
    }

    @Test
    void testSubscribeMultiplePoints() throws InterruptedException {
        // 准备测试数据
        List<String> pointIds = Arrays.asList("point-1", "point-2", "point-3");
        for (String pointId : pointIds) {
            subscription.addMockRuntime(pointId, new TestPointRuntime(pointId));
        }
        
        CountDownLatch subscribeLatch = new CountDownLatch(pointIds.size());
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        
        // 执行订阅
        subscription.subscribe(pointIds);
        
        // 验证所有点位都已订阅
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        for (String pointId : pointIds) {
            assertTrue(subscription.subscribed(pointId));
        }
        assertEquals(pointIds.size(), subscription.size());
    }

    @Test
    void testUnsubscribeSinglePoint() throws InterruptedException {
        // 先订阅一个点位
        String pointId = "test-point-1";
        TestPointRuntime runtime = new TestPointRuntime(pointId);
        subscription.addMockRuntime(pointId, runtime);
        
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        subscription.subscribe(Collections.singletonList(pointId));
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        
        // 执行取消订阅
        CountDownLatch unsubscribeLatch = new CountDownLatch(1);
        subscription.setUnsubscribeCallback(() -> unsubscribeLatch.countDown());
        subscription.unsubscribe(Collections.singletonList(pointId));
        
        // 验证取消订阅完成
        assertTrue(unsubscribeLatch.await(5, TimeUnit.SECONDS));
        assertFalse(subscription.subscribed(pointId));
        assertFalse(subscription.containsKey(pointId));
        assertTrue(subscription.isEmpty());
    }

    @Test
    void testUnsubscribeMultiplePoints() throws InterruptedException {
        // 先订阅多个点位
        List<String> pointIds = Arrays.asList("point-1", "point-2", "point-3");
        for (String pointId : pointIds) {
            subscription.addMockRuntime(pointId, new TestPointRuntime(pointId));
        }
        
        CountDownLatch subscribeLatch = new CountDownLatch(pointIds.size());
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        subscription.subscribe(pointIds);
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        
        // 执行取消订阅
        CountDownLatch unsubscribeLatch = new CountDownLatch(pointIds.size());
        subscription.setUnsubscribeCallback(() -> unsubscribeLatch.countDown());
        subscription.unsubscribe(pointIds);
        
        // 验证所有点位都已取消订阅
        assertTrue(unsubscribeLatch.await(5, TimeUnit.SECONDS));
        for (String pointId : pointIds) {
            assertFalse(subscription.subscribed(pointId));
        }
        assertTrue(subscription.isEmpty());
    }

    @Test
    void testReload() throws InterruptedException {
        // 先订阅一些点位
        List<String> pointIds = Arrays.asList("point-1", "point-2");
        for (String pointId : pointIds) {
            subscription.addMockRuntime(pointId, new TestPointRuntime(pointId));
        }
        
        CountDownLatch subscribeLatch = new CountDownLatch(pointIds.size());
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        subscription.subscribe(pointIds);
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        
        // 执行重新加载
        CountDownLatch reloadLatch = new CountDownLatch(pointIds.size());
        subscription.setSubscribeCallback(() -> reloadLatch.countDown());
        subscription.reload();
        
        // 验证重新加载完成
        assertTrue(reloadLatch.await(5, TimeUnit.SECONDS));
        for (String pointId : pointIds) {
            assertTrue(subscription.subscribed(pointId));
        }
    }

    @Test
    void testSubscribeWithError() throws InterruptedException {
        String pointId = "error-point";
        subscription.addMockRuntime(pointId, new TestPointRuntime(pointId));
        subscription.setSubscribeError(new RuntimeException("订阅失败"));
        
        CountDownLatch errorLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            errorLatch.countDown();
            return null;
        }).when(mockListener).onSubscribeFailed(eq(pointId), any(Throwable.class));
        
        // 执行订阅
        subscription.subscribe(Collections.singletonList(pointId));
        
        // 验证错误处理
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        assertFalse(subscription.subscribed(pointId));
        verify(mockListener).onSubscribeFailed(eq(pointId), any(RuntimeException.class));
        // 验证监控器的logger方法被调用
        verify(subscription.monitor(), atLeastOnce()).logger();
    }

    @Test
    void testUnsubscribeWithError() throws InterruptedException {
        // 先订阅一个点位
        String pointId = "test-point";
        TestPointRuntime runtime = new TestPointRuntime(pointId);
        subscription.addMockRuntime(pointId, runtime);
        
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        subscription.setSubscribeCallback(() -> subscribeLatch.countDown());
        subscription.subscribe(Collections.singletonList(pointId));
        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        
        // 设置取消订阅错误
        subscription.setUnsubscribeError(new RuntimeException("取消订阅失败"));
        
        // 执行取消订阅
        subscription.unsubscribe(Collections.singletonList(pointId));
        
        // 稍等一下让错误处理完成
        Thread.sleep(1000);
        
        // 验证错误处理，但点位仍然被移除
        // 验证监控器的logger方法被调用
        verify(subscription.monitor(), atLeastOnce()).logger();
        assertFalse(subscription.containsKey(pointId));
    }

    @Test
    void testGetPointRuntimeNotFound() {
        String pointId = "non-existent-point";
        
        StepVerifier.create(subscription.getPointRuntime(pointId))
                .verifyComplete();
        
        // 验证订阅不存在的点位
        subscription.subscribe(Collections.singletonList(pointId));
        
        // 稍等一下让处理完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertFalse(subscription.subscribed(pointId));
    }

    @Test
    void testBufferSize() {
        assertEquals(50, subscription.getBufferSize());
        
        // 测试自定义缓冲区大小
        TestPointSubscription customSubscription = new TestPointSubscription(mockListener) {
            @Override
            protected int getBufferSize() {
                return 10;
            }
        };
        assertEquals(10, customSubscription.getBufferSize());
    }

    @Test
    @Timeout(10)
    void testConcurrentSubscriptionOperations() throws InterruptedException {
        int threadCount = 10;
        int pointsPerThread = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 准备测试数据
        for (int i = 0; i < threadCount * pointsPerThread; i++) {
            String pointId = "point-" + i;
            subscription.addMockRuntime(pointId, new TestPointRuntime(pointId));
        }
        
        // 创建多个线程同时进行订阅操作
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    List<String> pointIds = new ArrayList<>();
                    for (int j = 0; j < pointsPerThread; j++) {
                        pointIds.add("point-" + (threadIndex * pointsPerThread + j));
                    }
                    
                    subscription.subscribe(pointIds);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        // 启动所有线程
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get());
        
        // 验证所有点位都被正确订阅
        Thread.sleep(2000); // 等待订阅操作完成
        assertEquals(threadCount * pointsPerThread, subscription.size());
    }

    @Test
    void testDispose() {
        assertFalse(subscription.isDisposed());
        
        subscription.dispose();
        
        assertTrue(subscription.isDisposed());
        assertTrue(subscription.disposed);
    }

    @Test
    void testSubscribingPointClass() {
        TestPointRuntime runtime = new TestPointRuntime("test-point");
        TestSubscribingPoint subscribingPoint = new TestSubscribingPoint(runtime);
        
        assertEquals(runtime, subscribingPoint.getPoint());
        assertFalse(subscribingPoint.subscribed);
        
        subscribingPoint.subscribed = true;
        assertTrue(subscribingPoint.subscribed);
    }

    @Test
    void testMonitorMethod() {
        // 验证监控器方法不为空且可以正常调用
        Monitor monitor = subscription.monitor();
        assertNotNull(monitor);
        assertNotNull(monitor.logger());
    }

    /**
     * 测试用的 AbstractPointSubscription 实现类
     */
    private static class TestPointSubscription extends AbstractPointSubscription<TestPointRuntime, TestSubscribingPoint> {
        
        private final Map<String, TestPointRuntime> mockRuntimes = new HashMap<>();
        private final Monitor monitor;
        private RuntimeException subscribeError;
        private RuntimeException unsubscribeError;
        private Runnable subscribeCallback;
        private Runnable unsubscribeCallback;
        boolean disposed = false;

        public TestPointSubscription(PointListener listener) {
            super(listener);
            this.monitor = mock(Monitor.class);
            // 创建一个简单的日志记录器模拟
            Logger mockLogger = mock(Logger.class);
            when(monitor.logger()).thenReturn(mockLogger);
        }

        @Override
        public Monitor monitor() {
            return monitor;
        }

        @Override
        public Mono<TestPointRuntime> getPointRuntime(String id) {
            TestPointRuntime runtime = mockRuntimes.get(id);
            if (runtime == null) {
                return Mono.empty();
            }
            return Mono.just(runtime);
        }

        @Override
        protected TestSubscribingPoint createSubscribing(TestPointRuntime runtime) {
            return new TestSubscribingPoint(runtime);
        }

        @Override
        protected Mono<Void> subscribe(List<TestSubscribingPoint> subscribing) {
            if (subscribeError != null) {
                return Mono.error(subscribeError);
            }
            
            return Mono.fromRunnable(() -> {
                for (TestSubscribingPoint point : subscribing) {
                    point.subscribed = true;
                    if (subscribeCallback != null) {
                        subscribeCallback.run();
                    }
                }
            });
        }

        @Override
        protected Mono<Void> unsubscribe(List<TestSubscribingPoint> subscribing) {
            if (unsubscribeError != null) {
                return Mono.error(unsubscribeError);
            }
            
            return Mono.fromRunnable(() -> {
                for (TestSubscribingPoint point : subscribing) {
                    point.subscribed = false;
                    if (unsubscribeCallback != null) {
                        unsubscribeCallback.run();
                    }
                }
            });
        }

        @Override
        protected void doDispose() {
            disposed = true;
        }
        
        // 测试辅助方法
        public void addMockRuntime(String id, TestPointRuntime runtime) {
            mockRuntimes.put(id, runtime);
        }
        
        public void setSubscribeError(RuntimeException error) {
            this.subscribeError = error;
        }
        
        public void setUnsubscribeError(RuntimeException error) {
            this.unsubscribeError = error;
        }
        
        public void setSubscribeCallback(Runnable callback) {
            this.subscribeCallback = callback;
        }
        
        public void setUnsubscribeCallback(Runnable callback) {
            this.unsubscribeCallback = callback;
        }
    }

    /**
     * 测试用的 PointRuntime 实现类
     */
    private static class TestPointRuntime implements DataCollectorProvider.PointRuntime {
        private final String id;

        public TestPointRuntime(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Mono<Result<Health>> test() {
            return Mono.just(Result.success(Health.ok()));
        }

        @Override
        public Mono<Result<PointData>> read() {
            return Mono.just(Result.success(new PointData()));
        }

        @Override
        public Mono<Result<PointData>> write(PointData data) {
            return Mono.just(Result.success(data));
        }

        @Override
        public Mono<DataCollectorProvider.State> checkState() {
            return Mono.empty();
        }

        @Override
        public DataCollectorProvider.State state() {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public reactor.core.Disposable onStateChanged(java.util.function.BiConsumer<DataCollectorProvider.State, DataCollectorProvider.State> listener) {
            return null;
        }

        @Override
        public boolean isDisposed() {
            return false;
        }

        @Override
        public <R> R execute(org.jetlinks.core.command.Command<R> command) {
            return null;
        }
    }

    /**
     * 测试用的 SubscribingPoint 实现类
     */
    private static class TestSubscribingPoint extends AbstractPointSubscription.SubscribingPoint<TestPointRuntime> {
        public TestSubscribingPoint(TestPointRuntime point) {
            super(point);
        }
    }
}