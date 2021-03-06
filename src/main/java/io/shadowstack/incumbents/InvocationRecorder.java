package io.shadowstack.incumbents;

import io.shadowstack.invocations.Invocation;
import io.shadowstack.invocations.InvocationContext;
import io.shadowstack.throttles.Throttle;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import io.shadowstack.filters.ObjectFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@Slf4j
public class InvocationRecorder implements MethodInterceptor, Consumer<FluxSink<Invocation>> {
    private static final ScheduledExecutorService THREAD_POOL =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private static final Scheduler SCHEDULER = Schedulers.fromExecutorService(THREAD_POOL);
    private final Set<FluxSink<Invocation>> listeners = new HashSet<>();
    private Flux<Invocation> flux;
    private ObjectFilter objectFilter;
    @Getter private final Object originalInstance;
    @Getter private Throttle throttle = null;

    public InvocationRecorder(Object originalInstance) {
        this.originalInstance = originalInstance;
    }

    public InvocationRecorder filteringWith(ObjectFilter filter) {
        this.objectFilter = filter;
        return this;
    }

    public InvocationRecorder throttlingTo(Throttle throttle) {
        this.throttle = throttle;
        return this;
    }

    public InvocationRecorder sendingTo(InvocationSink invocationSink) {
        this.flux = Flux.create(this, FluxSink.OverflowStrategy.DROP);
        this.flux.publishOn(SCHEDULER)
                 .subscribeOn(SCHEDULER)
                 .buffer(invocationSink.getBatchSize())
                 .subscribe(invocationSink);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T buildProxy(Class<T> cls) {
        if(cls == null || !cls.isInstance(this.originalInstance)) {
            String message = "Invalid combination of class %s and original instance %s. Returning null.";
            String className = (cls != null)? cls.getSimpleName() : "null";
            log.warn(String.format(message, className, this.originalInstance.getClass().getSimpleName()));
            return null;
        }
        return (T) Enhancer.create(cls, this);
    }

    @Override
    public Object intercept(Object o, Method method, Object[] arguments, MethodProxy proxy) throws Throwable {
        Throwable exceptionThrown = null;
        Duration callDuration;
        Object result = null;
        Instant start = Instant.now();

        try {
            result = method.invoke(this.originalInstance, arguments);
        } catch(InvocationTargetException ite) {
            exceptionThrown = ite.getTargetException();
        } catch(Throwable t) {
            exceptionThrown = t;
        } finally {
            callDuration = Duration.between(start, Instant.now());
        }

        try(InvocationContext context = new InvocationContext()) {
            Invocation invocation = new Invocation(
                    method, context,
                    this.objectFilter.filterAsReferenceCopy(arguments),
                    this.objectFilter.filterAsReferenceCopy(result),
                    this.objectFilter.filterAsEvaluatedCopy(arguments),
                    this.objectFilter.filterAsEvaluatedCopy(result),
                    exceptionThrown, callDuration
            );
            if(this.getThrottle() == null || !this.getThrottle().reject()) {
                this.listeners.forEach(l -> l.next(invocation));
            }
        } catch(Throwable t) {
            String message = "While intercepting recorded incumbents. Method=%s, Args=%d, Object=%s.";
            String className = this.originalInstance.getClass().getSimpleName();
            log.error(String.format(message, method.getName(), arguments.length, className), t);
        }

        if(exceptionThrown != null) {
            throw exceptionThrown;
        }

        return result;
    }

    @Override
    public void accept(FluxSink<Invocation> listener) {
        this.listeners.add(listener); // TODO: Support full subscription life-cycle, with removal of listeners?
    }
}
