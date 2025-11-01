![mvn-golang](assets/git_banner.png)

[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 11.0+](https://img.shields.io/badge/java-11.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven Central](https://img.shields.io/maven-central/v/com.igormaznitsa/gosdk-wrapper-maven-plugin)](http://search.maven.org/#artifactdetails|com.igormaznitsa|gosdk-wrapper-maven-plugin|1.1.0|jar)
[![Maven 3.8.1+](https://img.shields.io/badge/maven-3.8.1%2b-green.svg)](https://maven.apache.org/)   
[![Arthur's acres sanctuary donation](assets/arthur_sanctuary_banner.png)](https://www.arthursacresanimalsanctuary.org/donate)

# Changelog

__1.1.0 (01-nov-2025)__

- improved parsing of SDK list to support many formats and be prepared for load SDK through site instead of
  store [#7](https://github.com/raydac/gosdk-wrapper-maven-plugin/issues/7)

__1.0.5 (30-jun-2025)__

- updated dependencies and fixed vulnerable dependency alert

[full changelog](https://github.com/raydac/gosdk-wrapper-maven-plugin/blob/master/CHANGELOG.md)

# What is it?

A simple Maven plugin that automates working with GoSDK in Maven projects. It handles downloading GoSDK, caching it in a
specified directory, and invoking its tools.

Originally, there was a project called [mvn-golang](https://github.com/raydac/mvn-golang) that provided similar
functionality. However, since then, the Go ecosystem has changed significantly. I decided to create a simpler plugin
focused solely on downloading and executing GoSDK, removing features related to package installation, repository
management, and processing. Now, this is just a Maven plugin dedicated to fetching and running Go tools.

# How to build?

Just clone the project and use [Maven](https://maven.apache.org).
Go into the project folder and execute maven command

```bash
mvn clean install
```

if you want to build examples, use

```bash
mvn clean install -Pexamples
```

# Mojo description

[The generated Maven mojo site.](https://raydac.github.io/gosdk-wrapper-maven-plugin-site/index.html)

# Examples

- [Hello World with conversion with test coverage result as JUnit](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-hello-world)
- [Example of preprocessing with JCP](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-preprocessing)
- [Use a library shared through Maven repository](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-repository)
- [A terminal application with CLUI](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-clui)
- [A GUI application with Fyne](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-fyne)
- [A terminal application with GoTerm](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-goterm)
- [A simple shooter game with Oak](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-oak-shooter)
- [Simple Protobuf based application](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-protobuf)
- [Build of NES emulator](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-exanple-nes)
- [Tetra3D engine example](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-tetra3d)
- [Ebitengine Flappy game example](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-ebitengine)
- [Primitive image generator example](gosdk-wrapper-maven-plugin-examples/gosdk-wrapper-maven-plugin-example-primitive)

# Add to a Maven project

The plugin doesn't provide any packaging so you should use one of regular packaging like `pom` (if you use `jar` then
maven injects a lot of default calls for Java specific plugins). Just add into the maven
pom.xml build section

```xml

<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>gosdk-wrapper-maven-plugin</artifactId>
    <version>1.1.0</version>
    <configuration>
        <goVersion>1.25.3</goVersion>
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

If you use something else than `pom` packaging then during build you can see a lot of notifications from standard
plugins provided by packaging, they know nothing about Go,
so you can just move their execution into `none` phase by execution id.
For instance

```xml

<plugins>
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
</plugins>
```

# Generate Maven project from archetype

There is provided archetype for plugin based maven projects in the maven repository. You can very easily to generate a
project through call:

```shell
mvn archetype:generate "-DarchetypeGroupId=com.igormaznitsa" "-DarchetypeArtifactId=gosdk-wrapper-maven-plugin-hello" "-DarchetypeVersion=1.1.0"
```

# GoSDK list site

By default, the plugin uses `https://storage.googleapis.com/golang/` to retrieve the list of available Go SDKs.  
However, there is a potential risk that Google may restrict anonymous access to this site or move it elsewhere.

To address this, the plugin provides the `sdkSite` parameter, which allows you to specify a custom URL for the SDK list
source.  
It also includes several predefined values:

- **AUTO** — Load the Go SDK list from the default location.
- **GOOGLE_APIS** — Load the Go SDK list from `https://storage.googleapis.com/golang/`.
- **GOSDK_SITE** — Load the Go SDK list from `https://go.dev/dl/`.

For example, to switch directly to the Go SDK web page, you can configure the plugin as follows:

```xml

<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>gosdk-wrapper-maven-plugin</artifactId>
    <version>1.1.0</version>
    <configuration>
        <sdkSite>GOSDK_SITE</sdkSite>
        <goVersion>1.25.3</goVersion>
    </configuration>
</plugin>
```

You can also provide a direct URI or even a local file path as the value of sdkSite. The plugin supports multiple
formats, including HTML, XML, JSON, and plain text.
You can find examples of these formats in
the [test resources directory](/gosdk-wrapper-maven-plugin/src/test/resources/sites).   