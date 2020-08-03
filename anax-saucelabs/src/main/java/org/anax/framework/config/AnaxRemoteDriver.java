package org.anax.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.anax.framework.configuration.AnaxDriver;
import org.anax.framework.controllers.WebController;
import org.anax.framework.controllers.WebDriverWebController;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;


@Configuration
@Slf4j
public class AnaxRemoteDriver {

    @Value("${sauce.lab.host:ondemand.eu-central-1.saucelabs.com}")
    String host;
    @Value("${sauce.lab.port:80}")
    String port;
    @Value("${sauce.lab.username:NOT_CONFIGURED}")
    String sauceUserName;
    @Value("${sauce.lab.key:NOT_CONFIGURED}")
    String sauceAccessKey;
    @Value("${sauce.lab.browser:Chrome}")
    String browser;
    @Value("${sauce.lab.browser.version:76}")
    String browserVersion;
    @Value("${sauce.lab.platform:macOS 10.13}")
    String platform;

    @Value("${anax.target.url:http://www.google.com}")
    String targetUrl;


    @ConditionalOnMissingBean
    @Bean
    public AnaxDriver getWebDriver() throws MalformedURLException {

        DesiredCapabilities capabilities = new DesiredCapabilities();
        //set your user name and access key to run tests in Sauce
        capabilities.setCapability("username", sauceUserName);
        //set your sauce labs access key
        capabilities.setCapability("accessKey", sauceAccessKey);
        //set browser
        capabilities.setCapability("browserName", browser);
        //set operating system to macOS version 10.13
        capabilities.setCapability("platform", platform);
        //set the browser version to 11.1
        capabilities.setCapability("version", browserVersion);
        //set the build name of the application
        capabilities.setCapability("build", "Onboarding Sample App - Java-TestNG");
        //set your test case name so that it shows up in Sauce Labs
        capabilities.setCapability("name", "1-first-test");

        return () -> {
            Augmenter augmenter = new Augmenter();
            WebDriver driver = augmenter.augment(new RemoteWebDriver(new URL("http://"+host+":"+port+"/wd/hub"), capabilities));
            driver.get(targetUrl);
            return driver;
        };
    }

    @ConditionalOnMissingBean
    @Bean
    public WebController getWebController(@Autowired AnaxDriver anaxDriver, @Value("${anax.defaultWaitSeconds:5}") Integer defaultWaitSeconds) throws Exception {
        return new WebDriverWebController(anaxDriver.getWebDriver(), anaxDriver, defaultWaitSeconds);
    }

}
