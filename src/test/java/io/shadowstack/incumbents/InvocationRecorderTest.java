package io.shadowstack.incumbents;

import io.shadowstack.*;
import io.shadowstack.invocations.Invocation;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import io.shadowstack.filters.Noise;
import io.shadowstack.filters.ObjectFilter;
import io.shadowstack.filters.Secret;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static io.shadowstack.Fluently.*;

@Slf4j
public class InvocationRecorderTest extends BaseTest {
    @Test
    public void testRecordNoiseSecretsNamed() throws InterruptedException, TimeoutException, ExecutionException {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        CompletableFuture<Invocation> future = new CompletableFuture<>();
        ObjectFilter filter = filter(
                noise().from(Foo.class).where(named("timestamp")),
                secrets().from(Foo.class).where(named("lastName")),
                noise().from(Baz.class).where(named("id")),
                secrets().from(Baz.class).where(named("salary"))
        );
        Bar proxy = record(bar)
                        .filteringWith(filter)
                        .sendingTo(new InvocationSink(invocations -> {
                            Invocation invocation = invocations.get(0);
                            log.info(name + ": got incumbents " + invocation);
                            future.complete(invocation);
                            return invocations;
                        }).withBatchSize(1))
                        .buildProxy(Bar.class);
        assertEquals(result, proxy.doSomethingShadowed(foo));
        Invocation invocation = future.get(5L, TimeUnit.SECONDS);

        assertNotNull(invocation.getReferenceArguments());
        assertTrue(invocation.getReferenceArguments().length > 0);
        assertTrue(invocation.getReferenceArguments()[0] instanceof Foo);
        Foo referenceFoo = (Foo) invocation.getReferenceArguments()[0];
        assertEquals(referenceFoo.getLastName(), DefaultValue.of(String.class));
        assertEquals(referenceFoo.getBaz().getSalary(), DefaultValue.of(Double.class));
        assertEquals(referenceFoo.getFirstName(), foo.getFirstName());
        assertEquals(referenceFoo.getTimestamp(), foo.getTimestamp());
        assertEquals(referenceFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(referenceFoo.getBaz().getHeight(), foo.getBaz().getHeight());
        assertEquals(referenceFoo.getBaz().getId(), foo.getBaz().getId());
        assertEquals(invocation.getReferenceResult(), result);

        assertNotNull(invocation.getEvaluatedArguments());
        assertTrue(invocation.getEvaluatedArguments().length > 0);
        assertTrue(invocation.getEvaluatedArguments()[0] instanceof Foo);
        Foo evaluatedFoo = (Foo) invocation.getEvaluatedArguments()[0];
        assertEquals(evaluatedFoo.getLastName(), DefaultValue.of(String.class));
        assertEquals(evaluatedFoo.getBaz().getSalary(), DefaultValue.of(Double.class));
        assertEquals(evaluatedFoo.getFirstName(), foo.getFirstName());
        assertEquals(evaluatedFoo.getTimestamp(), DefaultValue.of(LocalDateTime.class));
        assertEquals(evaluatedFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(evaluatedFoo.getBaz().getHeight(), foo.getBaz().getHeight());
        assertEquals(evaluatedFoo.getBaz().getId(), DefaultValue.of(Long.class));
        assertEquals(invocation.getEvaluatedResult(), result);
        log.info(name + " finishing.");
    }

    @Test
    public void testRecordNoiseSecretsAnnotated() throws InterruptedException, TimeoutException, ExecutionException {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        CompletableFuture<Invocation> future = new CompletableFuture<>();
        ObjectFilter filter = filter(
                noise().from(Foo.class).where(annotated(Noise.class)),
                secrets().from(Foo.class).where(annotated(Secret.class)),
                noise().from(Baz.class), // annotated is default predicate
                secrets().from(Baz.class) // annotated is default predicate
        );
        Bar proxy = record(bar)
                .filteringWith(filter)
                .sendingTo(new InvocationSink(invocations -> {
                    Invocation invocation = invocations.get(0);
                    log.info(name + ": got incumbents " + invocation);
                    future.complete(invocation);
                    return invocations;
                }).withBatchSize(1))
                .buildProxy(Bar.class);
        assertEquals(result, proxy.doSomethingShadowed(foo));
        Invocation invocation = future.get(5L, TimeUnit.SECONDS);

        assertNotNull(invocation.getReferenceArguments());
        assertTrue(invocation.getReferenceArguments().length > 0);
        assertTrue(invocation.getReferenceArguments()[0] instanceof Foo);
        Foo referenceFoo = (Foo) invocation.getReferenceArguments()[0];
        assertEquals(referenceFoo.getLastName(), DefaultValue.of(String.class));
        assertEquals(referenceFoo.getBaz().getSalary(), foo.getBaz().getSalary(), 0.001D);
        assertEquals(referenceFoo.getFirstName(), foo.getFirstName());
        assertEquals(referenceFoo.getTimestamp(), foo.getTimestamp());
        assertEquals(referenceFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(referenceFoo.getBaz().getHeight(), (float)DefaultValue.of(Float.class), 0.001D);
        assertEquals(referenceFoo.getBaz().getId(), foo.getBaz().getId());
        assertEquals(invocation.getReferenceResult(), result);

        assertNotNull(invocation.getEvaluatedArguments());
        assertTrue(invocation.getEvaluatedArguments().length > 0);
        assertTrue(invocation.getEvaluatedArguments()[0] instanceof Foo);
        Foo evaluatedFoo = (Foo) invocation.getEvaluatedArguments()[0];
        assertEquals(evaluatedFoo.getLastName(), DefaultValue.of(String.class));
        assertEquals(evaluatedFoo.getBaz().getSalary(), foo.getBaz().getSalary(), 0.001D);
        assertEquals(evaluatedFoo.getFirstName(), foo.getFirstName());
        assertEquals(evaluatedFoo.getTimestamp(), DefaultValue.of(LocalDateTime.class));
        assertEquals(evaluatedFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(evaluatedFoo.getBaz().getHeight(), (float)DefaultValue.of(Float.class), 0.001D);
        assertEquals(evaluatedFoo.getBaz().getId(), DefaultValue.of(Long.class));
        assertEquals(invocation.getEvaluatedResult(), result);
        log.info(name + " finishing.");
    }

    @Test
    public void testThrownExceptionIsPropagated() throws TimeoutException, InterruptedException {
        String name = new Object() {
        }.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        ObjectFilter filter = filter(
                noise().from(Foo.class),
                secrets().from(Foo.class),
                noise().from(Baz.class),
                secrets().from(Baz.class)
        );
        Bar proxy = record(bar)
                        .filteringWith(filter)
                        .sendingTo(new InvocationSink(invocations -> {
                            return invocations;
                        }).withBatchSize(1))
                        .buildProxy(Bar.class);
        assertThrows(NotImplementedException.class, () -> proxy.doSomethingBad(foo));
    }

    @Test
    public void testZeroPercentThrottling() throws TimeoutException, InterruptedException {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        ObjectFilter filter = filter(
                noise().from(Foo.class),
                secrets().from(Foo.class),
                noise().from(Baz.class),
                secrets().from(Baz.class)
        );
        Bar proxy = record(bar)
                .filteringWith(filter)
                .throttlingTo(
                        percent(1.0)
                )
                .sendingTo(new InvocationSink(invocations -> {
                    log.info(name + ": got batch of size " + invocations.size());
                    threadAssertEquals(20, invocations.size());
                    resume();
                    return invocations;
                }).withBatchSize(20))
                .buildProxy(Bar.class);
        for(int i=0; i<100; ++i) {
            assertEquals(result, proxy.doSomethingShadowed(foo));
        }
        await(5, TimeUnit.SECONDS, 5);
        log.info(name + " finishing.");
    }

    @Test
    public void testOneHundredPercentThrottling() throws TimeoutException, InterruptedException {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        ObjectFilter filter = filter(
                noise().from(Foo.class),
                secrets().from(Foo.class),
                noise().from(Baz.class),
                secrets().from(Baz.class)
        );
        Bar proxy = record(bar)
                .filteringWith(filter)
                .throttlingTo(
                        percent(0.0)
                )
                .sendingTo(new InvocationSink(invocations -> {
                    fail("Transmit should never have been called.");
                    resume();
                    return invocations;
                }).withBatchSize(1))
                .buildProxy(Bar.class);
        for(int i=0; i<100; ++i) {
            assertEquals(result, proxy.doSomethingShadowed(foo));
        }
        assertThrows(TimeoutException.class, () -> await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testRateThrottling() throws InterruptedException, TimeoutException {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        ObjectFilter filter = filter(
                noise().from(Foo.class),
                secrets().from(Foo.class),
                noise().from(Baz.class),
                secrets().from(Baz.class)
        );
        Bar proxy = record(bar)
                .filteringWith(filter)
                .throttlingTo(
                        rate(2).per(1L, TimeUnit.SECONDS)
                )
                .sendingTo(new InvocationSink(invocations -> {
                    log.info(name + ": got batch of size " + invocations.size());
                    threadAssertEquals(4, invocations.size());
                    resume();
                    return invocations;
                }).withBatchSize(4))
                .buildProxy(Bar.class);
        for(int i=0; i<8; ++i) {
            assertEquals(result, proxy.doSomethingShadowed(foo));
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) { }
        }
        await(5, TimeUnit.SECONDS, 1);
        log.info(name + " finishing.");
    }

    @Test
    public void testNullClassYieldsNullProxy() {
        String name = new Object(){}.getClass().getEnclosingMethod().getName();
        log.info(name + " starting.");
        ObjectFilter filter = filter(
                noise().from(Foo.class),
                secrets().from(Foo.class),
                noise().from(Baz.class),
                secrets().from(Baz.class)
        );
        Bar proxy = record(bar)
                .filteringWith(filter)
                .throttlingTo(
                        rate(2).per(1L, TimeUnit.SECONDS)
                )
                .sendingTo(new InvocationSink(invocations -> {
                    log.info(name + ": got batch of size " + invocations.size());
                    threadAssertEquals(4, invocations.size());
                    resume();
                    return invocations;
                }).withBatchSize(4))
                .buildProxy(null);
        assertNull(proxy);
    }
}