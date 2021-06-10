package MySpringTest;


import MySpringTest.di.GreetingService;
import springCore.CommandLineRunner;
import springCore.MySpringApplication;
import springCore.annotations.Autowired;
import springCore.annotations.Component;

@Component
public class MySpring implements CommandLineRunner {
    @Autowired
    private GreetingService greetingService;


    public static void main(String[] args) {
        MySpringApplication.ENABLE_LOG = false;
        MySpringApplication.run(MySpring.class);
    }


    @Override
    public void run() {
        System.out.println("Now the application is running");
        System.out.println("This is my spring");
        greetingService.greet();
    }

}
