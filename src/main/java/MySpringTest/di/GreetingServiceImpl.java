package MySpringTest.di;

import springCore.annotations.Autowired;
import springCore.annotations.PostConstruct;

public class GreetingServiceImpl implements GreetingService{
    @Autowired
    HelloTest helloTest;

    @PostConstruct
    public void post() {
        System.out.println("Greeting Service Impl is ready: " + helloTest.test());
    }

    @Override
    public void greet() {
        System.out.println("Simple greeting");
    }
}
