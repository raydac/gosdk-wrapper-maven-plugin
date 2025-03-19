# Hello World

You can use the special maven archetype to generate a compilable single module skeleton.

```bash
mvn archetype:generate -DarchetypeGroupId=com.igormaznitsa -DarchetypeArtifactId=gosdk-wrapper-maven-plugin-hello -DarchetypeVersion=1.0.3
```

It will generate project tree

```
go-sdk-hello-example
  |
  +--src
  |
  +--res
  |
  \-pom.xml
```

You can just start `mvn` in the root project folder and get built result in the `build` folder.
