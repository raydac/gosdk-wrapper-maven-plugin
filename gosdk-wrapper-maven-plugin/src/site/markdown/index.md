# gosdk-wrapper-maven-plugin

It is a maven plugin which was developed to automate download an appropriate GoSDK and call its tool from Maven project
during build process.

Just add code-snippet below into build section and the plugin will be started during build.

```xml

<plugin>
    <groupId>com.igormaznitsa</groupId>
    <artifactId>gosdk-wrapper-maven-plugin</artifactId>
    <version>1.0.5</version>
    <configuration>
        <goVersion>1.24.4</goVersion>
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
