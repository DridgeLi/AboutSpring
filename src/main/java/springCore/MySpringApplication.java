package springCore;

import springCore.annotations.After;
import springCore.annotations.Aspect;
import springCore.annotations.Before;
import springCore.annotations.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MySpringApplication {
    public static boolean ENABLE_LOG = true;
    private List<Object> beans;

    private Stream<Class> loadClasses(Class main) {
        // todo 深入学习IO后完成
    }

    public void creatBeans(Class main) {
        beans = loadClasses(main)
                .filter(aClass ->
                        Arrays.stream(aClass.getAnnotations())
                                .anyMatch(annotation -> annotation instanceof Component))
                .map(aClass -> {
                    try {
                        return aClass.getConstructor().newInstance(); //对每一个Component创建实例
                    } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toList());

    }

    public void aop() {
        //Stream处理Aspect类
        Stream.Builder<MyAop> myAopBuilder = Stream.builder();

        //将注解有Aspect的类放入MyAop中
        for (Object bean : beans) {
            boolean ifAspect = Arrays.stream(bean.getClass().getAnnotations()).anyMatch(annotation -> annotation instanceof Aspect);
            if (ifAspect) {
                for (Method method : bean.getClass().getMethods()) {
                    for (Annotation annotation : method.getAnnotations()) {
                        if (annotation instanceof Before) {
                            String target = ((Before) annotation).value();
                            myAopBuilder.accept(aspectStringToMyAop(target, AdviceEnum.BEFORE,bean,method));
                        }
                        if (annotation instanceof After) {
                            String target = ((After) annotation).value();
                            myAopBuilder.accept(aspectStringToMyAop(target, AdviceEnum.AFTER, bean, method));
                        }
                    }
                }
            }
        }
        Map<String, Map<String, List < MyAop >>> clsAopMap = myAopBuilder.build().collect(
                Collectors.groupingBy(MyAop::getTargetClass, Collectors.groupingBy(MyAop::getTargetMethod)));

    }

    private MyAop aspectStringToMyAop(String aspectInjTarget, AdviceEnum adviceEnum, Object bean, Method method) {
        int index = aspectInjTarget.lastIndexOf(".");
        String targetClass = aspectInjTarget.substring(0, index);
        String targetMethod = aspectInjTarget.substring(index + 1);
        return new MyAop(targetClass, targetMethod, bean, adviceEnum, method);
    }

    public static void run(Class main) {
        //todo
    }

}
