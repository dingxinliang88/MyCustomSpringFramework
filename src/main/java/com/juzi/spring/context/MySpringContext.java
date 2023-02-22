package com.juzi.spring.context;

import com.juzi.spring.annotation.Component;
import com.juzi.spring.annotation.MyComponentScan;
import com.juzi.spring.annotation.Scope;
import com.juzi.spring.config.MySpringConfig;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器类
 *
 * @author codejuzi
 */
public class MySpringContext {
    /**
     * 保存配置类对象，获取扫描包
     */
    private Class<MySpringConfig> configClass;

    /**
     * 过滤出要处理的类名的后缀
     */
    private static final String PROCESS_FILE_PATH_SUFFIX = ".class";

    /**
     * 默认的scope value
     */
    private static final String DEFAULT_SCOPE_VALUE = "singleton";

    /**
     * 存放bean的定义
     */
    private final ConcurrentHashMap<String, BeanDefinition> beanDefinitionMap;

    /**
     * bean 单例池
     */
    private final ConcurrentHashMap<String, Object> singletonObjects;


    public MySpringContext(Class<MySpringConfig> configClass) {
        this.configClass = configClass;
        singletonObjects = new ConcurrentHashMap<>();
        beanDefinitionMap = new ConcurrentHashMap<>();
        initBeanDefinitionMap();
        initSingletonObjects();
    }

    /**
     * 扫描包，初始化Bean Definition Map
     */
    public void initBeanDefinitionMap() {

        // 获取扫描包
        MyComponentScan myComponentScan = this.configClass.getDeclaredAnnotation(MyComponentScan.class);
        String originPath = myComponentScan.value();

        // 得到类加载器
        ClassLoader classLoader = this.getClass().getClassLoader();

        // 得到扫描包的资源路径
        String proPath = originPath.replaceAll("\\.", "/");
        URL resource = classLoader.getResource(proPath);

        // 遍历需要加载的资源路径下的所有文件
        assert resource != null;
        File resourceFile = new File(resource.getFile());
        if (resourceFile.isDirectory()) {
            File[] files = resourceFile.listFiles();
            assert files != null;
            for (File file : files) {
                String fileAbsolutePath = file.getAbsolutePath();
                // 过滤出.class文件
                if (fileAbsolutePath.endsWith(PROCESS_FILE_PATH_SUFFIX)) {
                    // 获取到类名
                    int startIndex = fileAbsolutePath.lastIndexOf("/") + 1;
                    int endIndex = fileAbsolutePath.indexOf(PROCESS_FILE_PATH_SUFFIX);
                    String className = fileAbsolutePath.substring(startIndex, endIndex);
                    // 获取全类名（类的完整路径）
                    String classFullName = originPath + "." + className;

                    // 判断类是否是Spring Bean
                    try {
                        Class<?> clazz = classLoader.loadClass(classFullName);
                        if (clazz.isAnnotationPresent(Component.class)) {
                            // 封装bean信息到BeanDefinition
                            BeanDefinition beanDefinition = new BeanDefinition();
                            beanDefinition.setClazz(clazz);
                            // 得到beanName
                            Component component = clazz.getDeclaredAnnotation(Component.class);
                            String beanName = component.value();
                            // beanName校验
                            if ("".equals(beanName)) {
                                beanName = StringUtils.uncapitalize(className);
                            }
                            // 得到bean的Scope
                            if (clazz.isAnnotationPresent(Scope.class)) {
                                // 有Scope注解
                                Scope scopeAnnotation = clazz.getDeclaredAnnotation(Scope.class);
                                beanDefinition.setScopeValue(scopeAnnotation.value());
                            } else {
                                // 无Scope注解
                                beanDefinition.setScopeValue(DEFAULT_SCOPE_VALUE);
                            }
                            beanDefinitionMap.put(beanName, beanDefinition);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 通过beanDefinitionMap 初始化单例池Singleton Objects
     */
    public void initSingletonObjects() {
        for (Map.Entry<String, BeanDefinition> beanDefinitionEntry : beanDefinitionMap.entrySet()) {
            // 得到beanName
            String beanName = beanDefinitionEntry.getKey();
            // 通过beanName得到BeanDefinition
            BeanDefinition beanDefinition = this.beanDefinitionMap.get(beanName);
            // 得到scope value
            String scopeValue = beanDefinition.getScopeValue();
            if (DEFAULT_SCOPE_VALUE.equals(scopeValue)) {
                // 单例，初始化bean实例存入单例池
                Object bean = this.createBean(beanDefinition);
                this.singletonObjects.put(beanName, bean);
            }
        }
    }

    /**
     * 根据BeanDefinition创建Bean
     *
     * @param beanDefinition bean 信息
     * @return bean
     */
    private Object createBean(BeanDefinition beanDefinition) {
        // 得到bean的class对象
        Class<?> clazz = beanDefinition.getClazz();

        Object instance = null;

        // 通过反射创建bean实例
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return instance;
    }

    /**
     * 根据beanName获取bean实例
     *
     * @param beanName bean's name in bean definition map
     * @return bean实例
     */
    public Object getBean(String beanName) {
        // bean不存在，抛出异常
        if(!beanDefinitionMap.containsKey(beanName)) {
            throw new NullPointerException(String.format("%s is not exist in bean definition map", beanName));
        }

        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
        String scopeValue = beanDefinition.getScopeValue();
        if(DEFAULT_SCOPE_VALUE.equals(scopeValue)) {
            // bean存在，且为singleton，从单例池返回
            return singletonObjects.get(beanName);
        } else {
            // bean存在，且为prototype，创建实例返回
            return createBean(beanDefinition);
        }
    }
}
