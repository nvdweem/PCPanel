package dev.niels.elgato.jsonrpc;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import lombok.extern.log4j.Log4j2;

@Log4j2
final class JsonRpcWrapper {
    private JsonRpcWrapper() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> serviceType, JsonRpcSender sender) {
        return (T) Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[] { serviceType },
                (_, method, args) -> invoke(sender, method, args)
        );
    }

    @Nullable
    private static Object invoke(JsonRpcSender sender, Method method, Object[] args) {
        var commandName = method.getName();
        var params = (args != null && args.length > 0) ? args[0] : null;
        var returnType = method.getReturnType();
        var isAsync = CompletionStage.class.isAssignableFrom(returnType);
        var resultClass = resolveResultClass(method, isAsync);

        log.debug("Invoking {} with params {} and result class {}", commandName, params, resultClass);
        var future = sender.sendExpectingResult(new JsonRpcCommand<>(commandName, params), resultClass);
        if (isAsync) {
            log.debug("Returning future for {}", commandName);
            return future;
        }

        if (void.class.equals(returnType) || Void.class.equals(returnType)) {
            log.debug("Ignoring result for void method {}", commandName);
            return null;
        }
        log.debug("Waiting for result of {} with params {}", commandName, params);
        return future.join();
    }

    private static Class<?> resolveResultClass(Method method, boolean isAsync) {
        if (isAsync) {
            var genericReturn = method.getGenericReturnType();
            if (genericReturn instanceof ParameterizedType pt) {
                var typeArg = pt.getActualTypeArguments()[0];
                if (typeArg instanceof Class<?> c) {
                    return c;
                }
            }
            return Object.class;
        }

        var returnType = method.getReturnType();
        if (void.class.equals(returnType) || Void.class.equals(returnType)) {
            return Void.class;
        }
        return returnType;
    }
}
