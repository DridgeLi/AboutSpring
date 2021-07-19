package springCore;

import springCore.annotations.*;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MySpringApplication {
    public static boolean ENABLE_LOG = true;
    private List<Object> beans = new ArrayList<>();
    private Object[] proxyBeans;
    private CommandLineRunner runner;

    public static void run(Class<?> main) {
        MySpringApplication app = new MySpringApplication();
        try {
            app.creatBeans(main);
            app.aop();
            app.di();
            app.post();
            log("My Spring init successfully......");
            if (app.runner != null) {
                app.runner.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载classes文件目录下的，非springCore的，应用程序的类文件
     *
     * @param main creatBeans()方法传入的main参数，类型是Class。根据Class进行同级及字目录.class文件的加载
     * @return 返回一个包含Class的Stream供creatBeans()方法使用
     */
    private Stream<Class<?>> loadClasses(Class<?> main) throws MalformedURLException, ClassNotFoundException {
        URL resource = main.getResource("");
        File baseDir = new File(resource.getFile());

        //使用队列遍历应用程序目录下的所有文件
        Queue<File> dirs = new LinkedList<>();
        dirs.add(baseDir);

        //classes文件根目录的字符串长度，便于后续针对类名clsName进行辨别
        int offset = main.getResource("/").getPath().length();

        Stream.Builder<Class<?>> clsBuilder = Stream.builder();

        while (!dirs.isEmpty()) {
            File dir = dirs.poll();
            for (File f : Objects.requireNonNull(dir.listFiles())) {
                //将子目录加入队列
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    //File.toURI()方法会将路径中的非法字符串进行转义；将File转换为URL，使用：File.toURI().toURL()
                    String clsName = f.toURI().toURL().getPath().substring(offset).replaceAll("/", ".").replace(".class", "");
                    //将Core文件排除在外
                    if (!clsName.contains("springCore")) {
                        Class<?> cls = main.getClassLoader().loadClass(clsName);
                        log(String.format("Load class: [%s]", cls.getName()));
                        clsBuilder.accept(cls);
                    }
                }
            }
        }
        return clsBuilder.build();
    }

    /**
     * 对注解了@Component的创建实例，并以数组列表形式存储在全局变量beans中
     *
     * @param main run()方法传入的main参数，类型是Class。提供给loadClasses()方法，根据Class进行同级及字目录.class文件的加载
     */
    private void creatBeans(Class<?> main) throws MalformedURLException, ClassNotFoundException {
        beans = loadClasses(main)
                .filter(aClass -> Arrays.stream(aClass.getAnnotations()).anyMatch(annotation -> annotation instanceof Component))
                .map(aClass -> {
                    try {
                        return aClass.getConstructor().newInstance(); //对每一个Component创建实例
                    } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toList());

        runner = (CommandLineRunner) beans.stream().filter(bean -> Arrays.asList(bean.getClass().getInterfaces())
                .contains(CommandLineRunner.class)).findFirst().orElse(null);
    }

    /**
     * 提取标注了@Aspect的bean，根据标注@Before/@After生成代理对象，以数组形式存储在proxyBeans中
     */
    private void aop() {
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
                            myAopBuilder.accept(aspectStringToMyAop(target, AdviceEnum.BEFORE, bean, method));
                        }
                        if (annotation instanceof After) {
                            String target = ((After) annotation).value();
                            myAopBuilder.accept(aspectStringToMyAop(target, AdviceEnum.AFTER, bean, method));
                        }
                    }
                }
            }
        }
        //将MyAop的Stream流转换为 <类名,<方法名, Aop>> 的Map存储结构。
        Map<String, Map<String, List<MyAop>>> clsAopMap = myAopBuilder.build().collect(
                Collectors.groupingBy(MyAop::getTargetClass, Collectors.groupingBy(MyAop::getTargetMethod)));

        //代理的执行;将动态生成的代理对象放置在proxyBeans数组中；并且与beans数组保持一样的大小，相同下标表示bean有对应的proxyInstance
        proxyBeans = new Object[beans.size()];
        for (int i = 0; i < beans.size(); i++) {
            Object bean = beans.get(i);
            String clsName = bean.getClass().getName();
            if (clsAopMap.containsKey(clsName)) {
                Class<?>[] interfaces = bean.getClass().getInterfaces();
                if (interfaces.length > 0) {
                    //动态创建代理对象
                    Object proxyInstance = Proxy.newProxyInstance(clsName.getClass().getClassLoader(), interfaces, (proxy, method, args) -> {
                        List<MyAop> aops = clsAopMap.get(clsName).get(method.getName());
                        //执行前段方法
                        runAop(aops, AdviceEnum.BEFORE, method, args);
                        //包装目标对象
                        Object res = method.invoke(bean, args);
                        //执行后段方法
                        runAop(aops, AdviceEnum.AFTER, method, args);
                        return res;
                    });
                    proxyBeans[i] = proxyInstance;
                    log(String.format("ProxyInstance [%s] for [%s] created.", proxyInstance.getClass().getName(), clsName));
                }
            }
        }
    }

    /**
     * 提供给aop()方法，进行代理的切面方法的调用执行
     *
     * @param aops       存储代理类的列表
     * @param adviceEnum 根据传入的Before或者After，取出对应的MyAop进行调用
     * @param method     代理对象的目标方法
     * @param args       对象对象目标方法的参数
     */
    private void runAop(List<MyAop> aops, AdviceEnum adviceEnum, Method method, Object[] args) {
        if (aops != null) {
            aops.stream().filter(myAop -> myAop.getAdviceEnum() == adviceEnum).forEach(myAop -> {
                try {
                    myAop.getMethod().invoke(myAop.getAspect(), method, args);
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }


    /**
     * 专供aop()使用的方法，将Before和After的value()字符串，转换为Aspect的目标类和目标方法，并返回一个封装了数据的MyAop类
     */
    private MyAop aspectStringToMyAop(String aspectInjTarget, AdviceEnum adviceEnum, Object bean, Method method) {
        int index = aspectInjTarget.lastIndexOf(".");
        String targetClass = aspectInjTarget.substring(0, index);
        String targetMethod = aspectInjTarget.substring(index + 1);
        return new MyAop(targetClass, targetMethod, bean, adviceEnum, method);
    }

    /**
     * 遍历bean，对标注了@Autowired的field进行依赖注入
     */
    private void di() throws IllegalAccessException {
        for (Object bean : beans) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                //筛选标注了Autowired的字段进行依赖注入
                if (Arrays.stream(field.getAnnotations()).anyMatch(a -> a instanceof Autowired)) {
                    Class<?> fieldType = field.getType();
                    //从getRequiredBean中返回与fieldType对应的proxyInstance或者bean或者null
                    Object requiredBean = getRequireBean(fieldType);
                    if (requiredBean != null) {
                        field.setAccessible(true);
                        //执行依赖注入
                        field.set(bean, requiredBean);
                        log(String.format("Filed [%s] has annotation @Autowired, execute di, [%s].", field.toString(), requiredBean.getClass().getName()));
                    }
                }
            }
        }

    }

    /**
     * 供di()方法调用，根据参数fieldType是否是接口，或者返回对应的proxyInstance，或者返回bean
     *
     * @param fieldType 由di()传入的目标filed类型
     * @return 返回bean中查找到的与fieldType匹配的proxyInstance或者bean或者null
     */
    private Object getRequireBean(Class<?> fieldType) {
        if (fieldType.isInterface()) {
            for (int i = 0; i < beans.size(); i++) {
                Object bean = beans.get(i);
                for (Class<?> clsInterface : bean.getClass().getInterfaces()) {
                    if (clsInterface.equals(clsInterface)) {
                        return proxyBeans[i] != null ? proxyBeans[i] : bean;
                    }
                }
            }
        } else {
            return beans.stream().filter(bean -> bean.getClass().equals(fieldType)).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * 应用程序的类初始化完成后，应用程序中标注了@PostConstruct的方法优先执行
     */
    private void post() throws InvocationTargetException, IllegalAccessException {
        for (Object bean : beans) {
            for (Method method : bean.getClass().getMethods()) {
                if (Arrays.stream(method.getAnnotations()).anyMatch(a -> a instanceof PostConstruct)) {
                    method.setAccessible(true);
                    method.invoke(bean);
                    log(String.format("Post construct: method [%s] of [%s]", method.getName(), bean.getClass().getName()));
                }
            }
        }
    }

    private static void log(String msg) {
        if (ENABLE_LOG) {
            System.out.println(msg);
        }
    }

}
