package io.shadowstack;

import io.shadowstack.candidates.InvocationReplayer;
import lombok.experimental.UtilityClass;
import io.shadowstack.converters.Conversion;
import io.shadowstack.filters.FieldFilter;
import io.shadowstack.filters.Noise;
import io.shadowstack.filters.ObjectFilter;
import io.shadowstack.filters.Secret;
import io.shadowstack.incumbents.InvocationRecorder;
import io.shadowstack.throttles.Percentage;
import io.shadowstack.throttles.Rate;
import io.shadowstack.throttles.Throttle;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@UtilityClass
public class Fluently {
    public static Throttle percent(double percent) {
        return new Percentage(percent);
    }

    public static Rate rate(int max) {
        return new Rate(max);
    }

    public static Throttle every(long timeDuration, TimeUnit timeUnit) {
        return new Rate(1).per(timeDuration, timeUnit);
    }

    public static Predicate<Field> named(String... names) {
        return new NamesPredicate(names);
    }

    private static class NamesPredicate implements Predicate<Field> {
        private final Set<String> nameSet = new HashSet<>();

        public NamesPredicate(String... names) {
            this.nameSet.addAll(Arrays.asList(names));
        }

        @Override
        public boolean test(Field field) {
            return field != null && this.nameSet.contains(field.getName());
        }
    }

    public static Predicate<Field> annotated(Class<? extends Annotation> cls) {
        return field -> {
            if(field == null || cls == null) return false;
            return field.getDeclaredAnnotation(cls) != null;
        };
    }

    public static ObjectFilter filter(FieldFilter.Builder... fieldFilterBuilders) {
        return new ObjectFilter(Arrays.stream(fieldFilterBuilders).map(FieldFilter.Builder::build).toArray(FieldFilter[]::new));
    }

    public static FieldFilter.Builder noise() {
        // Noise is ignored, meaning that it gets scrubbed in the evaluated
        // object copies but kept unaltered in the reference object copies.
        return new FieldFilter.Builder(
                (obj) -> DefaultValue.of(obj.getClass()),
                (obj) -> obj
        ).where(annotated(Noise.class));
    }

    public static FieldFilter.Builder secrets() {
        // Secrets are redacted, meaning that they're scrubbed in both the
        // evaluated object copies and the reference object copies.
        return new FieldFilter.Builder(
                (obj) -> DefaultValue.of(obj.getClass()),
                (obj) -> DefaultValue.of(obj.getClass())
        ).where(annotated(Secret.class));
    }

    public static InvocationRecorder record(Object target) {
        return new InvocationRecorder(target);
    }

    public static <T> InvocationReplayer<T> replay(Class<T> cls) {
        return new InvocationReplayer<>(cls);
    }

    public static <T> Conversion.Builder.Initial<T> from(Class<T> from) {
        return new Conversion.Builder.Initial<>(from);
    }

    // replay(Foo.class)
    //      .performingConversions(
    //              from(Bar.class).to(Baz.class).with(orikaMapperFacade),
    //              from(Thing.class).to(Something.class).with(mapStructMapper)
    //      )
}