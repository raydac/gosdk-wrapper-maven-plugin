![mvn-golang](assets/git_banner.png)

[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 11.0+](https://img.shields.io/badge/java-11.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.igormaznitsa/gosdk-wrapper-maven-plugin/badge.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|gosdk-wrapper-maven-plugin|1.0.1|jar)
[![Maven 3.8.1+](https://img.shields.io/badge/maven-3.8.1%2b-green.svg)](https://maven.apache.org/)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-cyan.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![YooMoney donation](https://img.shields.io/badge/donation-Yoo.money-blue.svg)](https://yoomoney.ru/to/41001158080699)

# Changelog

__1.0.2 (SNAPSHOT)__

    - improved debug logging
    - disabled mass tracing log messages from Apache Http 5 Client
    - fixed wrong std output log file destination

__1.0.1 (12-mar-2025)__

    - fixed stuck [#1](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/1)
    - added flag `hideLoadIndicator` to hide GoSDK loading bar from log
    - fixed wrong default value for `workDir`

__1.0.0 (03-mar-2025)__

    - initial version

[full changelog](https://github.com/raydac/gosdk-wrapper-maven-plugin/blob/master/CHANGELOG.md)

# What is it?

A simple Maven plugin that automates working with GoSDK in Maven projects. It handles downloading GoSDK, caching it in a
specified directory, and invoking its tools.

Originally, there was a project called [mvn-golang](https://github.com/raydac/mvn-golang) that provided similar
functionality. However, since then, the Go ecosystem has changed significantly. I decided to create a simpler plugin
focused solely on downloading and executing GoSDK, removing features related to package installation, repository
management, and processing. Now, this is just a Maven plugin dedicated to fetching and running Go tools.

# Mojos on board

## execute

Download a GoSDK if not cached and find a tool to be executed with provided environment variables.

## delete-folders

Some Go packages can't be removed by regular maven clean plugin because can contain read only files. This mojo allows to
remove such folders.

# How to use

Let's take a look at the call go to print its help. Just add the plugin into the module build section.

```xml

<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>gosdk-wrapper-maven-plugin</artifactId>
    <version>1.0.1</version>
    <configuration>
        <goVersion>1.24.0</goVersion>
    </configuration>
    <executions>
        <execution>
            <id>go-help</id>
            <goals>
                <goal>execute</goal>
            </goals>
            <configuration>
                <args>
                    <arg>help</arg>
                </args>
            </configuration>
        </execution>
    </executions>
</plugin>
```

So now it will download and cached GoSDK and make call go tool with help CLI argument.
In the same time you can see log records of a lot of another maven plugin dedicated to Java project. You can disable
them with below code snippet.

```xml

<plugin>
    <artifactId>maven-clean-plugin</artifactId>
    <executions>
        <execution>
            <id>default-clean</id>
            <phase>none</phase>
        </execution>
    </executions>
</plugin>
<plugin>
<artifactId>maven-jar-plugin</artifactId>
<executions>
    <execution>
        <id>default-jar</id>
        <phase>none</phase>
    </execution>
</executions>
</plugin>
<plugin>
<artifactId>maven-surefire-plugin</artifactId>
<executions>
    <execution>
        <id>default-test</id>
        <phase>none</phase>
    </execution>
</executions>
</plugin>
<plugin>
<artifactId>maven-compiler-plugin</artifactId>
<executions>
    <execution>
        <id>default-compile</id>
        <phase>none</phase>
    </execution>
    <execution>
        <id>default-testCompile</id>
        <phase>none</phase>
    </execution>
</executions>
</plugin>
<plugin>
<artifactId>maven-install-plugin</artifactId>
<executions>
    <execution>
        <id>default-install</id>
        <phase>none</phase>
    </execution>
</executions>
</plugin>
```
