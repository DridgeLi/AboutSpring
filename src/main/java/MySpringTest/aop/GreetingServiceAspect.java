package MySpringTest.aop;

import springCore.annotations.After;
import springCore.annotations.Before;

import java.lang.reflect.Method;

public class GreetingServiceAspect {
    @Before(value = "MySpringTest.di.GreetingServiceImpl.greet")
    public void beforeAdvice(Method method, Object... args) {
        System.out.println("Before method:" + method);
    }


    @After(value = "MySpringTest.di.GreetingServiceImpl.greet")
    public void afterAdvice(Method method, Object... args) {
        System.out.println("After method:" + method);
    }
}
