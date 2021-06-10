package springCore;

import java.lang.reflect.Method;

public class MyAop {
    private final String targetClass;
    private final String targetMethod;
    private final Object aspect;
    private final AdviceEnum adviceEnum;
    private final Method method;

    public MyAop(String targetClass, String targetMethod, Object aspect, AdviceEnum adviceEnum, Method method) {
        super();
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.aspect = aspect;
        this.adviceEnum = adviceEnum;
        this.method = method;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public Object getAspect() {
        return aspect;
    }

    public AdviceEnum getAdviceEnum() {
        return adviceEnum;
    }

    public Method getMethod() {
        return method;
    }
}
