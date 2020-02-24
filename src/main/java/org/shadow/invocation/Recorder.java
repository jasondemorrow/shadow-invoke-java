package org.shadow.invocation;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.shadow.filtering.ObjectFilter;
import org.shadow.throttling.Throttle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@Slf4j
public class Recorder implements MethodInterceptor, Consumer<FluxSink<Recording>> {
    private static final ScheduledExecutorService THREAD_POOL =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private static final Scheduler SCHEDULER = Schedulers.fromExecutorService(THREAD_POOL);
    private final Set<FluxSink<Recording>> listeners = new HashSet<>();
    private final boolean isShadowing;
    private Flux<Recording> flux;
    private ObjectFilter objectFilter;
    @Getter private final Object originalInstance;
    @Getter private Throttle throttle = null;

    public Recorder(Object originalInstance) {
        this.originalInstance = originalInstance;
        this.isShadowing = false;
    }

    public Recorder(Object originalInstance, boolean isShadowing) {
        this.originalInstance = originalInstance;
        this.isShadowing = isShadowing;
    }

    public Recorder filteringWith(ObjectFilter filter) {
        this.objectFilter = filter;
        return this;
    }

    public Recorder throttlingTo(Throttle throttle) {
        this.throttle = throttle;
        return this;
    }

    public Recorder savingTo(Record record) {
        this.flux = Flux.create(this, FluxSink.OverflowStrategy.DROP);
        this.flux.publishOn(SCHEDULER)
                 .subscribeOn(SCHEDULER)
                 .buffer(record.getBatchSize())
                 .subscribe(record);
        return this;
    }

    public <T> T proxyingAs(Class<T> cls) {
        if(cls == null || !cls.isInstance(this.originalInstance)) {
            String message = "Invalid combination of class %s and original instance %s. Returning null.";
            String className = (cls != null)? cls.getSimpleName() : "null";
            log.warn(String.format(message, className, this.originalInstance.getClass().getSimpleName()));
            return null;
        }
        return (T)Enhancer.create(cls, this);
    }

    @Override
    public Object intercept(Object o, Method method, Object[] arguments, MethodProxy proxy) throws Throwable {
        Object result = method.invoke(this.originalInstance, arguments); // TODO: How to replay exceptions?
        try(Recording.InvocationContext context = new Recording.InvocationContext()) {
            Recording recording = new Recording(
                    this.originalInstance.getClass(), method, context, this.objectFilter.filterAsReferenceCopy(arguments),
                    this.objectFilter.filterAsReferenceCopy(result), this.objectFilter.filterAsEvaluatedCopy(arguments),
                    this.objectFilter.filterAsEvaluatedCopy(result)
            );
            if(this.getThrottle() == null || !this.getThrottle().reject()) {
                this.listeners.forEach(l -> l.next(recording));
            }
        } catch(Throwable t) {
            String message = "While intercepting recorded invocation. Method=%s, Args=%d, Object=%s.";
            String className = this.originalInstance.getClass().getSimpleName();
            log.error(String.format(message, method.getName(), arguments.length, className), t);
        }
        return result;
    }

    @Override
    public void accept(FluxSink<Recording> listener) {
        this.listeners.add(listener); // TODO: Support full subscription life-cycle, with removal of listeners?
    }
}
