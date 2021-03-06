package com.lambdaworks.redis.commands.rx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.lambdaworks.redis.internal.AbstractInvocationHandler;
import rx.Observable;

import com.lambdaworks.redis.api.StatefulConnection;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.sync.RedisCommands;
import com.lambdaworks.redis.cluster.api.StatefulRedisClusterConnection;
import com.lambdaworks.redis.internal.LettuceLists;
import com.lambdaworks.redis.internal.LettuceSets;
import com.lambdaworks.redis.sentinel.api.StatefulRedisSentinelConnection;
import com.lambdaworks.redis.sentinel.api.sync.RedisSentinelCommands;

/**
 * Invocation handler for testing purposes.
 * @param <K>
 * @param <V>
 */
public class RxSyncInvocationHandler<K, V> extends AbstractInvocationHandler {

    private final StatefulConnection<?, ?> connection;
    private final Object rxApi;

    public RxSyncInvocationHandler(StatefulConnection<?, ?> connection, Object rxApi) {
        this.connection = connection;
        this.rxApi = rxApi;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {

        try {

            Method targetMethod = rxApi.getClass().getMethod(method.getName(), method.getParameterTypes());

            Object result = targetMethod.invoke(rxApi, args);

            if (result == null || !(result instanceof Observable<?>)) {
                return result;
            }
            Observable<?> observable = (Observable<?>) result;

            if (!method.getName().equals("exec") && !method.getName().equals("multi")) {
                if (connection instanceof StatefulRedisConnection && ((StatefulRedisConnection) connection).isMulti()) {
                    observable.subscribe();
                    return null;
                }
            }

            List<?> value = observable.toList().toBlocking().first();

            if (method.getReturnType().equals(List.class)) {
                return value;
            }

            if (method.getReturnType().equals(Set.class)) {
                return LettuceSets.newHashSet(value);
            }

            if (!value.isEmpty()) {
                return value.get(0);
            }

            return null;

        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    public static <K, V> RedisCommands<K, V> sync(StatefulRedisConnection<K, V> connection) {

        RxSyncInvocationHandler<K, V> handler = new RxSyncInvocationHandler<>(connection, connection.reactive());
        return (RedisCommands<K, V>) Proxy.newProxyInstance(handler.getClass().getClassLoader(),
                new Class<?>[] { RedisCommands.class }, handler);
    }

    public static <K, V> RedisCommands<K, V> sync(StatefulRedisClusterConnection<K, V> connection) {

        RxSyncInvocationHandler<K, V> handler = new RxSyncInvocationHandler<>(connection, connection.reactive());
        return (RedisCommands<K, V>) Proxy.newProxyInstance(handler.getClass().getClassLoader(),
                new Class<?>[] { RedisCommands.class }, handler);
    }

    public static <K, V> RedisSentinelCommands<K, V> sync(StatefulRedisSentinelConnection<K, V> connection) {

        RxSyncInvocationHandler<K, V> handler = new RxSyncInvocationHandler<>(connection, connection.reactive());
        return (RedisSentinelCommands<K, V>) Proxy.newProxyInstance(handler.getClass().getClassLoader(),
                new Class<?>[] { RedisSentinelCommands.class }, handler);
    }
}