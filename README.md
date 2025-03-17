![mvn-golang](assets/git_banner.png)

[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 11.0+](https://img.shields.io/badge/java-11.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven central](https://img.shields.io/badge/maven-central-1.0.3%2b-green.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|gosdk-wrapper-maven-plugin|1.0.2|jar)
[![Maven 3.8.1+](https://img.shields.io/badge/maven-3.8.1%2b-green.svg)](https://maven.apache.org/)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-cyan.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![YooMoney donation](https://img.shields.io/badge/donation-Yoo.money-blue.svg)](https://yoomoney.ru/to/41001158080699)

# Changelog

__1.0.3 (SNAPSHOT)__

- improved examples
- added `path` into `execute` mojo
- refactoring

__1.0.2 (16-mar-2025)__

- added `give-all-permissions` mojo
- fixed exception under Windows (#3)
- improved debug logging
- disabled mass tracing log messages from Apache Http 5 Client
- fixed wrong std output log file destination

[full changelog](https://github.com/raydac/gosdk-wrapper-maven-plugin/blob/master/CHANGELOG.md)

# What is it?

A simple Maven plugin that automates working with GoSDK in Maven projects. It handles downloading GoSDK, caching it in a
specified directory, and invoking its tools.

Originally, there was a project called [mvn-golang](https://github.com/raydac/mvn-golang) that provided similar
functionality. However, since then, the Go ecosystem has changed significantly. I decided to create a simpler plugin
focused solely on downloading and executing GoSDK, removing features related to package installation, repository
management, and processing. Now, this is just a Maven plugin dedicated to fetching and running Go tools.

# Add to a Maven project

The plugin doesn't provide any packaging so you should use one of regular packaging like `pom` (if you use `jar` then maven injects a lot of default calls for Java specific plugins). Just add into the maven
pom.xml build section

```xml

<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>gosdk-wrapper-maven-plugin</artifactId>
    <version>1.0.2</version>
    <configuration>
        <goVersion>1.24.1</goVersion>
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

It will automatically download GoSDK and cache it but keep in mind that the plugin makes minimalistic business and it
doesn't provide any extra options and environment variables just out of the box, also it doesn't make any installation
and deploy of projects.

If you use something else than `pom` packaging then during build you can see a lot of notifications from standard plugins provided by packaging, they know nothing about Go,
so you can just move their execution into `none` phase by execution id.
For instance

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
```

# Mojos on board

Take a look
at [the description](https://html-preview.github.io/?url=https://github.com/raydac/gosdk-wrapper-maven-plugin/blob/main/mojo-doc-site/plugin-info.html)

## execute

Download a GoSDK if not cached and find a tool to be executed with provided environment variables.

## delete-folders

Some Go packages can't be removed by regular maven clean plugin because can contain read only files. This mojo allows to
remove such folders.

## give-all-permissions

Trying to provide all permissions to files in specified folders, it works with list of Maven FileSets.
