package org.shadow.invoke.client;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.shadow.invoke.Bar;
import org.shadow.invoke.Baz;
import org.shadow.invoke.Foo;
import org.shadow.invoke.Task;
import org.shadow.invoke.core.Invocation;
import org.shadow.invoke.core.InvocationCache;
import org.shadow.Redacted;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.shadow.invoke.client.InvocationShadower.shadow;
import static org.shadow.invoke.core.FieldFilter.from;

public class InvocationShadowerTest {
    private static final Bar bar = new Bar();
    private static final Baz baz = new Baz(
            "Pawn", 75000.00D, 69.5F, 1234L,
            ImmutableMap.of(TimeUnit.MINUTES, Task.Clerical, TimeUnit.HOURS, Task.Management)
    );
    private static final Foo foo = new Foo("Bob", "Smith", 35, LocalDateTime.now(), baz);

    @Test
    public void testShadow() {
        shadow(bar)
                .ignoring(
                        from(Foo.class).fields("timestamp"),
                        from(Baz.class).fields("id")
                )
                .redacting(
                        from(Foo.class).fields("lastName"),
                        from(Baz.class).fields("salary")
                )
                .invoke(Bar.class)
                .doSomethingShadowed(foo);
        Invocation recording = InvocationCache.INSTANCE.getThreadLocalRecording();
        assertNotNull(recording);
        assertEquals(recording.getOutput(), bar.doSomethingShadowed(foo));
        assertNotNull(recording.getInputs());
        assertTrue(recording.getInputs().size() > 0);
        assertTrue(recording.getInputs().get(0) instanceof Foo);
        Foo redactedFoo = (Foo)recording.getInputs().get(0);
        assertEquals(redactedFoo.getLastName(), Redacted.valueOf(String.class));
        assertEquals(redactedFoo.getBaz().getSalary(), Redacted.valueOf(Double.class));
        assertEquals(redactedFoo.getFirstName(), foo.getFirstName());
        assertEquals(redactedFoo.getTimestamp(), foo.getTimestamp());
        assertEquals(redactedFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(redactedFoo.getBaz().getHeight(), foo.getBaz().getHeight());
        assertEquals(redactedFoo.getBaz().getId(), foo.getBaz().getId());
    }

    @Test
    public void testShadowWithSingularIgnore() {
        shadow(bar)
                .ignore(
                        from(Baz.class).fields("id")
                )
                .redacting(
                        from(Foo.class).fields("lastName"),
                        from(Baz.class).fields("salary")
                )
                .invoke(Bar.class)
                .doSomethingShadowed(foo);
        Invocation recording = InvocationCache.INSTANCE.getThreadLocalRecording();
        assertNotNull(recording);
        assertEquals(recording.getOutput(), bar.doSomethingShadowed(foo));
        assertNotNull(recording.getInputs());
        assertTrue(recording.getInputs().size() > 0);
        assertTrue(recording.getInputs().get(0) instanceof Foo);
        Foo redactedFoo = (Foo)recording.getInputs().get(0);
        assertEquals(redactedFoo.getLastName(), Redacted.valueOf(String.class));
        assertEquals(redactedFoo.getBaz().getSalary(), Redacted.valueOf(Double.class));
        assertEquals(redactedFoo.getFirstName(), foo.getFirstName());
        assertEquals(redactedFoo.getTimestamp(), foo.getTimestamp());
        assertEquals(redactedFoo.getBaz().getTitle(), foo.getBaz().getTitle());
        assertEquals(redactedFoo.getBaz().getHeight(), foo.getBaz().getHeight());
        assertEquals(redactedFoo.getBaz().getId(), foo.getBaz().getId());
    }
}