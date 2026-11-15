package org.jetlinks.collector.subscribe;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LazyPointSubscriptionTest {

    @Test
    void removingPointsPreservesOtherSubscriptionsAndReloadedTarget() {
        LazyPointSubscription subscription = new LazyPointSubscription();
        subscription.subscribe(List.of("read", "subscribe"));
        TrackingSubscription first = new TrackingSubscription();
        subscription.reload(first);

        subscription.unsubscribe(List.of("read", "unknown"));
        assertEquals(Set.of("subscribe"), first.points);
        assertEquals(Set.of("read", "unknown"), first.removed);
        assertTrue(subscription.subscribed("subscribe"));
        assertFalse(subscription.subscribed("read"));

        TrackingSubscription second = new TrackingSubscription();
        subscription.reload(second);
        assertTrue(first.disposed);
        assertEquals(Set.of("subscribe"), second.points);
        subscription.unsubscribe(List.of("subscribe"));
        assertTrue(second.points.isEmpty());
        subscription.dispose();
        assertTrue(second.disposed);
    }

    private static final class TrackingSubscription implements PointSubscription {
        final Set<String> points = new HashSet<>();
        Set<String> removed = Set.of();
        boolean disposed;

        @Override public void subscribe(Collection<String> ids) { points.addAll(ids); }
        @Override public void unsubscribe(Collection<String> ids) { removed = Set.copyOf(ids); points.removeAll(ids); }
        @Override public boolean subscribed(String id) { return points.contains(id); }
        @Override public void reload() { }
        @Override public boolean isDisposed() { return disposed; }
        @Override public void dispose() { disposed = true; points.clear(); }
    }
}
