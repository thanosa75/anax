package org.anax.framework.annotations;


import org.anax.framework.configuration.AnaxSuiteRunner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.Arrays;

@Component
public class AnaxTestAnnotationProcessor implements BeanPostProcessor {

    private final ApplicationContext context;
    private final AnaxSuiteRunner suiteRunner;

    public AnaxTestAnnotationProcessor(@Autowired ApplicationContext context, @Autowired AnaxSuiteRunner suiteRunner) {
        this.context = context;
        this.suiteRunner = suiteRunner;
    }

    /**
     * for every bean that we receive, we check to see if the @Anax... annotations is declared on
     * it's methods or class. For every method that matches, we add this to our Test execution context
     * for later processing
     * @param bean the bean object
     * @param beanName the bean name
     * @return the object
     * @throws BeansException
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> aClass = bean.getClass();

        //class level:AnaxTest
        Arrays.stream(aClass.getAnnotations())
            .filter(classAnn->classAnn.annotationType() == AnaxTest.class)
            .findFirst().ifPresent(classAnnotation ->{
            // add bean in order to register as valid class for tests
            //todo register class

            // then, on method level:
            ReflectionUtils.doWithMethods(aClass, method -> {
                Arrays.stream(method.getDeclaredAnnotations()).filter(item -> item.annotationType() == AnaxBeforeTest.class)
                        .findFirst().ifPresent(testAnnotation -> {
                    AnaxBeforeTest beforeStep = (AnaxBeforeTest) testAnnotation;
                    //TODO this method needs to be added to our Test methods to execute BEFORE the step
                });

                Arrays.stream(method.getDeclaredAnnotations()).filter(item -> item.annotationType() == AnaxPreCondition.class)
                        .findFirst().ifPresent(testAnnotation -> {
                    AnaxPreCondition beforeStep = (AnaxPreCondition) testAnnotation;

                    if (!beforeStep.skip()) {
                        //TODO this method needs to be added to our Test methods to execute BEFORE the step
                        // in our Test Framework bean
                    }
                });

                Arrays.stream(method.getDeclaredAnnotations()).filter(item -> item.annotationType() == AnaxTestStep.class)
                        .findFirst().ifPresent(testAnnotation -> {
                    AnaxTestStep testStep = (AnaxTestStep) testAnnotation;

                    int order = testStep.ordering();
                    boolean skip = testStep.skip();
                    if (!skip) {
                        // add bean, method, order
                    }
                });

                Arrays.stream(method.getDeclaredAnnotations()).filter(item -> item.annotationType() == AnaxPostCondition.class)
                        .findFirst().ifPresent(testAnnotation -> {
                    AnaxPostCondition afterStep = (AnaxPostCondition) testAnnotation;

                    if (!afterStep.skip()) {
                        //TODO this method needs to be added to our Test methods to execute AFTER the step
                        // in our Test Framework bean
                    }
                });

                Arrays.stream(method.getDeclaredAnnotations()).filter(item -> item.annotationType() == AnaxAfterTest.class)
                        .findFirst().ifPresent(testAnnotation -> {
                    AnaxAfterTest beforeStep = (AnaxAfterTest) testAnnotation;
                    //TODO this method needs to be added to our Test methods to execute BEFORE the step
                });
            });



        });

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
