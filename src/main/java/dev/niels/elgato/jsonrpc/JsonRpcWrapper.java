package dev.niels.elgato.jsonrpc;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JavaType;

import dev.niels.elgato.jsonrpc.JsonRpcSender.JsonRpcCommand;
import lombok.extern.log4j.Log4j2;

@Log4j2
final class JsonRpcWrapper {
    private JsonRpcWrapper() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> serviceType, JsonRpcSender sender) {
        var cache = new HashMap<String, CacheEntry>();
        return (T) Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[] { serviceType },
                (_, method, args) -> invoke(sender, method, args, cache)
        );
    }

    @Nullable
    private static Object invoke(JsonRpcSender sender, Method method, Object[] args, Map<String, CacheEntry> cache) {
        var commandName = method.getName();
        var params = (args != null && args.length > 0) ? args[0] : null;
        var ce = cache.computeIfAbsent(commandName, cn -> {
            var returnType = method.getReturnType();
            var isAsync = CompletionStage.class.isAssignableFrom(returnType);
            return new CacheEntry(isAsync, sender.toJavaType(resolveResultClass(method, isAsync)));
        });

        log.debug("Invoking {} with params {} and result type {}", commandName, params, ce.resultType);
        var future = sender.sendExpectingResult(new JsonRpcCommand<>(commandName, params), ce.resultType);
        if (ce.isAsync) {
            log.debug("Returning future for {}", commandName);
            return future;
        }

        var raw = ce.resultType.getRawClass();
        if (void.class.equals(raw) || Void.class.equals(raw)) {
            log.debug("Ignoring result for void method {}", commandName);
            return null;
        }
        log.debug("Waiting for result of {} with params {}", commandName, params);
        return future.join();
    }

    private static Type resolveResultClass(Method method, boolean isAsync) {
        if (isAsync) {
            var genericReturn = method.getGenericReturnType();
            if (genericReturn instanceof ParameterizedType pt) {
                return pt.getActualTypeArguments()[0];
            }
            return Object.class;
        }

        var returnType = method.getReturnType();
        if (void.class.equals(returnType) || Void.class.equals(returnType)) {
            return Void.class;
        }
        return returnType;
    }

    record CacheEntry(boolean isAsync, JavaType resultType) {
    }
}
