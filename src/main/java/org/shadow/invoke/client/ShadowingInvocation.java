package org.shadow.invoke.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.shadow.invoke.core.FieldFilter;
import org.shadow.invoke.core.InvocationRecord;
import org.shadow.invoke.core.Recordings;

import java.lang.reflect.Method;
import java.util.*;

@Data
@Slf4j
public class ShadowingInvocation extends RecordingInvocation {
    private final List<FieldFilter> ignoredFields;

    protected ShadowingInvocation(Object instance, List<FieldFilter> redactedFields, List<FieldFilter> ignoredFields) {
        super(instance, redactedFields);
        this.ignoredFields = ignoredFields;
    }

    public static ShadowingInvocation shadow(Object shadowedInstance) {
        if(shadowedInstance == null) {
            throw new IllegalArgumentException("Can't create a shadowing invocation from a null instance.");
        }
        return new ShadowingInvocation(shadowedInstance, new ArrayList<>(), new ArrayList<>());
    }

    public ShadowingInvocation ignoring(FieldFilter... filters) {
        if(filters != null && filters.length > 0) {
            for(FieldFilter f : filters) {
                this.ignore(f);
            }
        } else {
            String message = "Bad ignore filters passed to shadow invocation for {}: {}";
            String className = this.getOriginalInstance().getClass().getSimpleName();
            log.warn(String.format(message, className, filters));
        }
        return this;
    }

    public ShadowingInvocation ignore(FieldFilter filter) {
        if(filter != null && filter.isValid()) {
            this.ignoredFields.add(filter);
        } else {
            String message = "Bad ignoring field filter passed to shadow invocation for {}: {}";
            String className = this.getOriginalInstance().getClass().getSimpleName();
            log.warn(String.format(message, className, filter));
        }
        return this;
    }

    @Override
    protected InvocationRecord startNewRecording(Object output, Method method, Object[] inputs) {
        return Recordings.INSTANCE.createAndSave(inputs, output, method, this.getRedactedFields(), this.ignoredFields);
    }
}
