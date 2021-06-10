package MySpringTest.di;

import springCore.annotations.Component;

@Component
public class HelloTest {
    public String test() {
        return "hello, test.";
    }

}